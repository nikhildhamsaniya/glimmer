package com.github.efung.nexus4wp;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import org.andengine.engine.camera.Camera;
import org.andengine.engine.handler.timer.ITimerCallback;
import org.andengine.engine.handler.timer.TimerHandler;
import org.andengine.engine.options.EngineOptions;
import org.andengine.engine.options.ScreenOrientation;
import org.andengine.engine.options.resolutionpolicy.RatioResolutionPolicy;
import org.andengine.entity.particle.BatchedSpriteParticleSystem;
import org.andengine.entity.particle.Particle;
import org.andengine.entity.particle.initializer.ColorParticleInitializer;
import org.andengine.entity.particle.initializer.RotationParticleInitializer;
import org.andengine.entity.particle.modifier.AlphaParticleModifier;
import org.andengine.entity.particle.modifier.ExpireParticleInitializer;
import org.andengine.entity.particle.modifier.IParticleModifier;
import org.andengine.entity.scene.Scene;
import org.andengine.entity.sprite.UncoloredSprite;
import org.andengine.extension.ui.livewallpaper.BaseLiveWallpaperService;
import org.andengine.input.sensor.orientation.IOrientationListener;
import org.andengine.input.sensor.orientation.OrientationData;
import org.andengine.opengl.texture.TextureOptions;
import org.andengine.opengl.texture.atlas.bitmap.BitmapTextureAtlas;
import org.andengine.opengl.texture.atlas.bitmap.BitmapTextureAtlasTextureRegionFactory;
import org.andengine.opengl.texture.bitmap.BitmapTextureFormat;
import org.andengine.opengl.texture.region.ITextureRegion;
import org.andengine.util.color.Color;
import org.andengine.util.math.MathUtils;

public class Nexus4LiveWallpaper extends BaseLiveWallpaperService implements SharedPreferences.OnSharedPreferenceChangeListener
{
    private static final int CAMERA_WIDTH = 480;
    private static final int CAMERA_HEIGHT = 800;

    private Camera mCamera;
    private BitmapTextureAtlas mBitmapTextureAtlas;
    private ITextureRegion mParticleTextureRegion;
    private BatchedSpriteParticleSystem mParticleSystem;

    // Prefs
    private int mMode;
    private float mParticleLifetime;
    private int mDotSize;

    private boolean mSettingsChanged = false;
    private float mCurrentPitch; // rotation around X-axis, screen's horizontal axis (tilting forward and backward)
    private float mCurrentRoll; // rotation around Y-axis, screen's vertical axis (tilting left and right)


    @Override
    public EngineOptions onCreateEngineOptions()
    {
        this.mCamera = new Camera(0, 0, CAMERA_WIDTH, CAMERA_HEIGHT);
        return new EngineOptions(true, ScreenOrientation.PORTRAIT_FIXED, new RatioResolutionPolicy(CAMERA_WIDTH, CAMERA_HEIGHT), this.mCamera);
    }

    @Override
    public void onCreateResources(OnCreateResourcesCallback pOnCreateResourcesCallback) throws Exception
    {
        readSettingsFromPreferences();
        this.mSettingsChanged = true;
        PreferenceManager.getDefaultSharedPreferences(Nexus4LiveWallpaper.this).registerOnSharedPreferenceChangeListener(this);

        BitmapTextureAtlasTextureRegionFactory.setAssetBasePath("gfx/");
        this.mBitmapTextureAtlas = new BitmapTextureAtlas(this.getTextureManager(), 32, 32, BitmapTextureFormat.RGB_565, TextureOptions.BILINEAR);

        pOnCreateResourcesCallback.onCreateResourcesFinished();
    }

    @Override
    public void onCreateScene(OnCreateSceneCallback pOnCreateSceneCallback) throws Exception
    {
        final Scene scene = new Scene();
        buildScene(scene);
        pOnCreateSceneCallback.onCreateSceneFinished(scene);
    }

    @Override
    public void onPopulateScene(Scene pScene, OnPopulateSceneCallback pOnPopulateSceneCallback) throws Exception
    {
        pOnPopulateSceneCallback.onPopulateSceneFinished();
    }

    @Override
    protected void onPause()
    {
        disableSensors();
        super.onPause();
    }


    @Override
    protected void onResume()
    {
        Scene scene = this.mEngine.getScene();

        if (mMode == Nexus4LWPPreferenceActivity.PREFS_MODE_REFLECT_LIGHT)
        {
            enableSensors();
        }
        if (mSettingsChanged)
        {
            resetScene();
            buildScene(scene);
            mSettingsChanged = false;
        }

        super.onResume();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preference, String s)
    {
        // TODO: Make this more fine grained. Reflect Light mode only cares if mode or dot size was changed?
        // We could also disable dot period for reflect light mode.
        if (s.equals(this.getString(R.string.prefs_key_mode)))
        {
            this.mSettingsChanged = true;
        }
        else if (s.equals(this.getString(R.string.prefs_key_dot_size)))
        {
            this.mSettingsChanged = true;
        }
        else if (s.equals(this.getString(R.string.prefs_key_particle_lifetime)))
        {
            this.mSettingsChanged = true;
        }
    }

    private void readSettingsFromPreferences()
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(Nexus4LiveWallpaper.this);

        this.mMode = Integer.valueOf(prefs.getString(this.getString(R.string.prefs_key_mode), String.valueOf(
                Nexus4LWPPreferenceActivity.PREFS_MODE_DEFAULT)));
        this.mDotSize = Integer.valueOf(prefs.getString(this.getString(R.string.prefs_key_dot_size), String.valueOf(
                Nexus4LWPPreferenceActivity.PREFS_DOT_SIZE_DEFAULT)));
        this.mParticleLifetime = prefs.getInt(this.getString(R.string.prefs_key_particle_lifetime), 6);
    }

    private String getParticleFilename(final int prefsDotSize)
    {
        switch (prefsDotSize)
        {
            case Nexus4LWPPreferenceActivity.PREFS_DOT_SIZE_L:
                return "nexusdot_l.png";
            case Nexus4LWPPreferenceActivity.PREFS_DOT_SIZE_M:
            default:
                return "nexusdot_m.png";
            case Nexus4LWPPreferenceActivity.PREFS_DOT_SIZE_S:
                return "nexusdot_s.png";
        }
    }

    private void loadParticleImage()
    {
        this.mParticleTextureRegion = BitmapTextureAtlasTextureRegionFactory.createFromAsset(this.mBitmapTextureAtlas, this, getParticleFilename(this.mDotSize), 0, 0);
        this.mBitmapTextureAtlas.load();
    }

    private void unloadParticleImage()
    {
        this.mBitmapTextureAtlas.unload();
        this.mParticleTextureRegion = null;
    }

    private void enableSensors()
    {
        this.enableOrientationSensor(new IOrientationListener()
        {
            @Override
            public void onOrientationChanged(OrientationData pOrientationData)
            {
                Nexus4LiveWallpaper.this.mCurrentPitch = pOrientationData.getPitch();
                Nexus4LiveWallpaper.this.mCurrentRoll = pOrientationData.getRoll();
            }

            @Override
            public void onOrientationAccuracyChanged(OrientationData pOrientationData)
            {
            }
        });
    }

    private void disableSensors()
    {
        this.mEngine.disableOrientationSensor(this);
    }

    private Color getRandomColor()
    {
        return new Color(MathUtils.RANDOM.nextInt(255) / 255f, MathUtils.RANDOM.nextInt(255) / 255f, MathUtils.RANDOM.nextInt(255) / 255f);
    }

    private void resetScene()
    {
        unloadParticleImage();
        this.mEngine.getScene().detachChildren();
        this.mEngine.clearUpdateHandlers();
    }

    private void buildScene(final Scene scene)
    {
        readSettingsFromPreferences();
        loadParticleImage();

        switch (mMode)
        {
            case Nexus4LWPPreferenceActivity.PREFS_MODE_CHANGE_COLOUR:
            default:
                buildColourChangeScene(scene);
                break;
            case Nexus4LWPPreferenceActivity.PREFS_MODE_REFLECT_LIGHT:
                buildReflectLightScene(scene);
                break;
        }
    }

    private void buildReflectLightScene(final Scene scene)
    {
        final GridParticleEmitter particleEmitter = new GridParticleEmitter(CAMERA_WIDTH * 0.5f,  CAMERA_HEIGHT * 0.5f, CAMERA_WIDTH, CAMERA_HEIGHT,
                this.mParticleTextureRegion.getWidth(), this.mParticleTextureRegion.getHeight());
        final int maxParticles = particleEmitter.getGridTilesX() * particleEmitter.getGridTilesY();
        this.mParticleSystem = new BatchedSpriteParticleSystem(particleEmitter, maxParticles, maxParticles, maxParticles,
                this.mParticleTextureRegion, this.getVertexBufferObjectManager());

        this.mParticleSystem.addParticleInitializer(new ColorParticleInitializer<UncoloredSprite>(getRandomColor()));
        this.mParticleSystem.addParticleInitializer(new RotationParticleInitializer<UncoloredSprite>(-90f, 90f));

        this.mParticleSystem.addParticleModifier(new IParticleModifier<UncoloredSprite>()
        {
            @Override
            public void onUpdateParticle(Particle<UncoloredSprite> pParticle)
            {
                UncoloredSprite sprite = pParticle.getEntity();
                final float theta = sprite.getRotation();
                if (Math.abs(Nexus4LiveWallpaper.this.mCurrentRoll) < 10f)
                {
                    sprite.setAlpha(getAlphaFromRotation(theta, Nexus4LiveWallpaper.this.mCurrentPitch));
                }
                else
                {
                    sprite.setAlpha(getAlphaFromRotation(theta, Nexus4LiveWallpaper.this.mCurrentRoll));
                }
            }

            @Override
            public void onInitializeParticle(Particle<UncoloredSprite> pParticle)
            {
                UncoloredSprite sprite = pParticle.getEntity();
                sprite.setAlpha(getAlphaFromRotation(sprite.getRotation(), 0));
            }


            private float getAlphaFromRotation(final float pParticleRotation, float tilt)
            {
                // The closer the particle angle is to the tilt angle, the brighter it is.
                // Can't really see screen if tilt is more than 45 degrees, so we clamp it.
                // Maximum difference will be 180 degrees, so normalize then convert to alpha.
                tilt = MathUtils.bringToBounds(-45, 45, tilt);
                return 1f - Math.abs(pParticleRotation - 2*tilt) / 180f;
            }
        });

        scene.attachChild(this.mParticleSystem);
    }

    private void buildColourChangeScene(final Scene scene)
    {
        final GridParticleEmitter particleEmitter = new GridParticleEmitter(CAMERA_WIDTH * 0.5f,  CAMERA_HEIGHT * 0.5f, CAMERA_WIDTH, CAMERA_HEIGHT,
                this.mParticleTextureRegion.getWidth(), this.mParticleTextureRegion.getHeight());
        final int maxParticles = particleEmitter.getGridTilesX() * particleEmitter.getGridTilesY();
        this.mParticleSystem = new BatchedSpriteParticleSystem(particleEmitter, maxParticles / (1.5f* mParticleLifetime), maxParticles / mParticleLifetime, maxParticles,
                this.mParticleTextureRegion, this.getVertexBufferObjectManager());

        Color initialColor = getRandomColor();
        ColorParticleInitializer<UncoloredSprite> colorParticleInitializer = new ColorParticleInitializer<UncoloredSprite>(initialColor);
        this.mParticleSystem.addParticleInitializer(colorParticleInitializer);
        this.mParticleSystem.addParticleInitializer(new RotationParticleInitializer<UncoloredSprite>(-90f, 90f));
        this.mParticleSystem.addParticleInitializer(new ExpireParticleInitializer<UncoloredSprite>(mParticleLifetime));

        this.mParticleSystem.addParticleModifier(
                new AlphaParticleModifier<UncoloredSprite>(0, mParticleLifetime / 2f, 0.3f, 1f));
        this.mParticleSystem.addParticleModifier(new AlphaParticleModifier<UncoloredSprite>(mParticleLifetime / 2f,
                mParticleLifetime, 1f, 0.3f));

        this.mEngine.registerUpdateHandler(new TimerHandler(mParticleLifetime * 0.8f, true, new ChangingColorParticleInitializerTimerHandler(this.mParticleSystem, initialColor, colorParticleInitializer)));

        scene.attachChild(this.mParticleSystem);
    }

    private class ChangingColorParticleInitializerTimerHandler implements ITimerCallback
    {
        private int mCurrentParticleColorARGB;
        private float hsv[] = new float[3];
        private ColorParticleInitializer<UncoloredSprite> mCurrentParticleInitializer;
        private BatchedSpriteParticleSystem mParticleSystem;

        public ChangingColorParticleInitializerTimerHandler(final BatchedSpriteParticleSystem pParticleSystem, final Color pColor, final ColorParticleInitializer<UncoloredSprite> pInitializer)
        {
            this.mParticleSystem = pParticleSystem;
            this.mCurrentParticleInitializer = pInitializer;
            this.mCurrentParticleColorARGB = pColor.getARGBPackedInt();
        }

        @Override
        public void onTimePassed(TimerHandler pTimerHandler)
        {
            android.graphics.Color.colorToHSV(this.mCurrentParticleColorARGB, hsv);

            // Perturb values by hue, avoiding overly dark or bright colours
            hsv[0] += MathUtils.randomSign() * (MathUtils.RANDOM.nextFloat() * 20.0f + 10.0f); // between 10 and 30 degrees
            if (hsv[0] > 360f) {
                hsv[0] -= 360f;
            }
            else if (hsv[0] < 0f) {
                hsv[0] += 360f;
            }
            hsv[1] = MathUtils.bringToBounds(0.3f, 0.7f, hsv[1] + MathUtils.randomSign() * 0.1f);
            hsv[2] = MathUtils.bringToBounds(0.3f, 0.7f, hsv[2] + MathUtils.randomSign() * 0.1f);

            this.mCurrentParticleColorARGB = android.graphics.Color.HSVToColor(hsv);

            final float r = android.graphics.Color.red(this.mCurrentParticleColorARGB) / 255f;
            final float g = android.graphics.Color.green(this.mCurrentParticleColorARGB) / 255f;
            final float b = android.graphics.Color.blue(this.mCurrentParticleColorARGB) / 255f;

            ColorParticleInitializer<UncoloredSprite> initializer = new ColorParticleInitializer<UncoloredSprite>(r, g, b);

            this.mParticleSystem.addParticleInitializer(initializer);
            this.mParticleSystem.removeParticleInitializer(this.mCurrentParticleInitializer);
            this.mCurrentParticleInitializer = initializer;
        }
    }

}
