package io.github.actforever.kuudra.awtrobot;

interface RobotDriver {
    void keyPress(int code);
    void keyRelease(int code);
    void mousePress(int mask);
    void mouseRelease(int mask);
    void mouseMove(int x, int y);
    void mouseWheel(int amount);
}
