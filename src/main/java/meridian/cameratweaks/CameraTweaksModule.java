package meridian.cameratweaks;

import java.util.OptionalInt;
import meridian.api.event.EventPriority;
import meridian.api.event.PhaseChangedEvent;
import meridian.api.module.ModuleContext;
import meridian.api.module.ProxyModule;
import meridian.api.session.SessionPhase;
import meridian.api.settings.SettingsSpec;
import meridian.core.api.CameraControl;
import meridian.core.api.EntityTracker;
import org.slf4j.Logger;

/**
 * meridian-camera-tweaks — a pure Layer-2 module.
 *
 * <p>Exposes four camera features through a declarative {@link SettingsSpec}:
 * freecam, first/third-person (third-person with a tunable offset), follow-cam
 * and entity POV. It owns no packet logic — every change is delegated to
 * meridian-core's {@link CameraControl} service, with target entities chosen via
 * {@link EntityTracker}. No {@code meridian.protocol} import; a Hytale protocol
 * change cannot reach this module.
 */
public class CameraTweaksModule implements ProxyModule {

    /** Crosshair pick cone — half-angle in degrees. */
    private static final double CROSSHAIR_ANGLE_DEG = 15.0;
    /** Crosshair pick range — blocks. */
    private static final double CROSSHAIR_RANGE = 40.0;

    private Logger log;
    private CameraControl camera;
    private EntityTracker entities;

    // Live settings — each is mirrored from a SettingsSpec callback.
    private volatile CameraMode mode = CameraMode.DEFAULT;
    private volatile int thirdPersonDistance = 4;
    private volatile int shiftX = 0;
    private volatile int shiftY = 0;
    private volatile int shiftZ = 0;
    private volatile boolean thirdPersonInverted = false;
    private volatile TargetMode targetMode = TargetMode.NEAREST;
    private volatile int followDistance = 6;
    private volatile int followShiftX = 0;
    private volatile int followShiftY = 0;
    private volatile int followShiftZ = 0;

    @Override
    public void onEnable(ModuleContext ctx) {
        this.log = ctx.getLogger();
        this.camera = ctx.services().require(CameraControl.class);
        this.entities = ctx.services().require(EntityTracker.class);

        // Re-apply the camera once a session is live. WORLD_LOADED covers the
        // entity-free modes; PLAYING is when ClientMovement/EntityUpdates flow,
        // so follow / POV target selection has data to work with.
        ctx.events().subscribe(PhaseChangedEvent.class, EventPriority.NORMAL, e -> {
            if (e.to() == SessionPhase.WORLD_LOADED || e.to() == SessionPhase.PLAYING) {
                apply();
            }
        });

        // Declarative settings — the proxy renders and persists them; each
        // callback fires with the persisted value at startup and on every edit.
        ctx.registerSettings(SettingsSpec.builder()
                .enum_("mode", "Camera Mode", CameraMode.class, CameraMode.DEFAULT, v -> {
                    mode = v;
                    apply();
                })
                // Session-only — the camera always starts in DEFAULT on a restart.
                .ephemeral()
                .section("Third Person", SettingsSpec.builder()
                        .int_("tpDistance", "Distance", 1, 20, 4, v -> {
                            thirdPersonDistance = v;
                            apply();
                        })
                        .int_("shiftX", "Shift X (blocks)", -20, 20, 0, v -> {
                            shiftX = v;
                            apply();
                        })
                        .int_("shiftY", "Shift Y (blocks)", -20, 20, 0, v -> {
                            shiftY = v;
                            apply();
                        })
                        .int_("shiftZ", "Shift Z (blocks)", -20, 20, 0, v -> {
                            shiftZ = v;
                            apply();
                        })
                        .bool("inverted", "Front view (invert camera)", false, v -> {
                            thirdPersonInverted = v;
                            apply();
                        })
                        .build())
                .section("Follow / POV", SettingsSpec.builder()
                        .enum_("target", "Target Selection", TargetMode.class, TargetMode.NEAREST, v -> {
                            targetMode = v;
                            apply();
                        })
                        .int_("followDistance", "Follow Distance", 1, 30, 6, v -> {
                            followDistance = v;
                            apply();
                        })
                        .int_("followShiftX", "Shift X (blocks)", -20, 20, 0, v -> {
                            followShiftX = v;
                            apply();
                        })
                        .int_("followShiftY", "Shift Y (blocks)", -20, 20, 0, v -> {
                            followShiftY = v;
                            apply();
                        })
                        .int_("followShiftZ", "Shift Z (blocks)", -20, 20, 0, v -> {
                            followShiftZ = v;
                            apply();
                        })
                        .build())
                .section("Freecam", SettingsSpec.builder()
                        .bool("autoGrantKey", "Always allow in-game freecam key", false, v -> {
                            camera.autoGrantFreecam(v);
                        })
                        .build())
                .build());

        log.info("meridian-camera-tweaks enabled — backed by meridian-core CameraControl");
    }

    @Override
    public void onDisable() {
        if (camera != null && camera.available()) {
            camera.reset();
        }
    }

    /**
     * Recomputes the desired camera from the current settings and pushes it.
     * Idempotent — safe to call on any setting edit or phase change. A no-op
     * until a client session is live.
     */
    private synchronized void apply() {
        if (camera == null || !camera.available()) {
            return;
        }
        switch (mode) {
            case DEFAULT -> {
                camera.freecam(false);
                camera.reset();
            }
            case FIRST_PERSON -> {
                camera.freecam(false);
                camera.firstPerson();
            }
            case THIRD_PERSON -> {
                camera.freecam(false);
                camera.thirdPerson(thirdPersonDistance, shiftX, shiftY, shiftZ,
                        thirdPersonInverted);
            }
            case FREECAM -> camera.freecam(true);
            case FOLLOW_ENTITY -> {
                camera.freecam(false);
                OptionalInt target = pickTarget();
                if (target.isPresent()) {
                    camera.followEntity(target.getAsInt(), followDistance,
                            followShiftX, followShiftY, followShiftZ);
                } else {
                    log.warn("camera-tweaks: FOLLOW_ENTITY — no {} entity found ({} tracked)",
                            targetMode, entities.trackedCount());
                }
            }
            case ENTITY_POV -> {
                camera.freecam(false);
                OptionalInt target = pickTarget();
                if (target.isPresent()) {
                    camera.entityView(target.getAsInt(),
                            followShiftX, followShiftY, followShiftZ);
                } else {
                    log.warn("camera-tweaks: ENTITY_POV — no {} entity found ({} tracked)",
                            targetMode, entities.trackedCount());
                }
            }
        }
    }

    /** Resolves the entity to attach to, per the configured {@link TargetMode}. */
    private OptionalInt pickTarget() {
        return switch (targetMode) {
            case NEAREST -> entities.nearestEntity();
            case CROSSHAIR -> entities.entityInCrosshair(CROSSHAIR_ANGLE_DEG, CROSSHAIR_RANGE);
        };
    }
}
