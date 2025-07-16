package com.example.drawbot;

/**
 * Simple SCARA Robot Kinematics
 * Handles inverse kinematics calculations for drawing
 */
public class ScaraKinematics {

    public static class ScaraConfig {
        public float arm1Length = 240.0f;  // Length of first arm (shoulder to elbow)
        public float arm2Length = 245.0f;  // Length of second arm (elbow to wrist)
        public float offsetX = 0.0f;       // X offset of shoulder joint
        public float offsetY = 100.0f;     // Y offset of shoulder joint
        public float penUpZ = 5.0f;        // Z coordinate when pen is up
        public float penDownZ = -1.0f;     // Z coordinate when pen is down
    }

    public static class Point {
        public float x, y, z;
        public Point(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public static class JointAngles {
        public float angle1, angle2;  // Joint angles in degrees
        public boolean valid;         // Whether the solution is valid

        public JointAngles(float angle1, float angle2, boolean valid) {
            this.angle1 = angle1;
            this.angle2 = angle2;
            this.valid = valid;
        }
    }

    /**
     * Calculate inverse kinematics for a given X,Y position
     */
    public static JointAngles inverseKinematics(float x, float y, ScaraConfig config) {
        // Translate to shoulder joint coordinate system
        float dx = x - config.offsetX;
        float dy = y - config.offsetY;

        // Distance from shoulder to target
        float distance = (float) Math.sqrt(dx * dx + dy * dy);

        // Check if target is reachable
        float maxReach = config.arm1Length + config.arm2Length;
        float minReach = Math.abs(config.arm1Length - config.arm2Length);

        if (distance > maxReach || distance < minReach) {
            return new JointAngles(0, 0, false);
        }

        // Calculate joint angles using law of cosines
        float cosAngle2 = (dx * dx + dy * dy - config.arm1Length * config.arm1Length - config.arm2Length * config.arm2Length)
                         / (2.0f * config.arm1Length * config.arm2Length);

        // Clamp to valid range to avoid numerical errors
        cosAngle2 = Math.max(-1.0f, Math.min(1.0f, cosAngle2));

        // Calculate angle2 (elbow angle) - use negative for "elbow up" configuration
        float angle2 = -(float) Math.acos(cosAngle2);

        // Calculate angle1 (shoulder angle)
        float k1 = config.arm1Length + config.arm2Length * (float) Math.cos(angle2);
        float k2 = config.arm2Length * (float) Math.sin(angle2);
        float angle1 = (float) Math.atan2(dy, dx) - (float) Math.atan2(k2, k1);

        // Convert to degrees
        angle1 = (float) Math.toDegrees(angle1);
        angle2 = (float) Math.toDegrees(angle2);

        return new JointAngles(angle1, angle2, true);
    }

    /**
     * Calculate forward kinematics for given joint angles
     */
    public static Point forwardKinematics(float angle1, float angle2, ScaraConfig config) {
        // Convert to radians
        float a1 = (float) Math.toRadians(angle1);
        float a2 = (float) Math.toRadians(angle2);

        // Calculate end effector position
        float x = config.arm1Length * (float) Math.cos(a1) +
                 config.arm2Length * (float) Math.cos(a1 + a2) + config.offsetX;
        float y = config.arm1Length * (float) Math.sin(a1) +
                 config.arm2Length * (float) Math.sin(a1 + a2) + config.offsetY;

        return new Point(x, y, 0);
    }

    /**
     * Check if a point is within the robot's workspace
     */
    public static boolean isPointReachable(float x, float y, ScaraConfig config) {
        JointAngles angles = inverseKinematics(x, y, config);
        return angles.valid;
    }
}
