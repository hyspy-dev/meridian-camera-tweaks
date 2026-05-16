package meridian.cameratweaks;

/** The camera state the module forces on the client. */
public enum CameraMode {
    /** Hand control back to the server's default player camera. */
    DEFAULT,
    /** Native first-person view. */
    FIRST_PERSON,
    /** Third-person view with a tunable distance and offset. */
    THIRD_PERSON,
    /** Free-fly spectator camera. */
    FREECAM,
    /** Spectator follow-cam trailing a selected entity. */
    FOLLOW_ENTITY,
    /** First-person POV from inside a selected entity. */
    ENTITY_POV
}
