package meridian.cameratweaks;

/** How {@code FOLLOW_ENTITY} / {@code ENTITY_POV} pick their target entity. */
public enum TargetMode {
    /** The entity nearest the local player. */
    NEAREST,
    /** The entity the local player is looking at. */
    CROSSHAIR
}
