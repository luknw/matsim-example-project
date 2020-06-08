package pl.edu.agh.matsim.networkReader;

/***
 * Values of the OSM turn tag along with an arbitrarily chosen angle representing direction.
 * The angles are given in terms of the atan2 function to give consistent results when comparing
 * with matsim link angles, which are calculated the same way.
 */
public enum OsmTurnTagValue {
    /**
     * Indicates that no information is provided on the real-world road.
     */
    NONE("n", Math.atan2(0.0, 1.0)),
    REVERSE("rv", Math.atan2(0.0, -1.0)),
    SHARP_LEFT("shl", Math.atan2(1.0, -1.0)),
    LEFT("l", Math.atan2(1.0, 0.0)),
    SLIGHT_LEFT("sl", Math.atan2(1.0, 1.0)),
    MERGE_TO_LEFT("ml", Math.atan2(0.0, 1.0)),
    THROUGH("t", Math.atan2(0.0, 1.0)),
    MERGE_TO_RIGHT("mr", Math.atan2(0.0, 1.0)),
    SLIGHT_RIGHT("sr", Math.atan2(-1.0, 1.0)),
    RIGHT("r", Math.atan2(-1.0, 0.0)),
    SHARP_RIGHT("shr", Math.atan2(-1.0, -1.0));

    private final String laneIdSuffix;
    private final double radAngle;

    OsmTurnTagValue(String laneIdSuffix, double radAngle) {
        this.laneIdSuffix = laneIdSuffix;
        this.radAngle = radAngle;
    }

    @Override
    public String toString() {
        return name().toLowerCase(); // lowercase to match the OSM tag convention
    }

    public String getLaneIdSuffix() {
        return laneIdSuffix;
    }

    public double getRadAngle() {
        return radAngle;
    }
}
