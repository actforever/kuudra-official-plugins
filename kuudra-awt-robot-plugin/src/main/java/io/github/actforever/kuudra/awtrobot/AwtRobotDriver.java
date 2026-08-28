package io.github.actforever.kuudra.awtrobot;

import io.github.actforever.kuudra.api.KuudraException;

import java.awt.AWTException;
import java.awt.GraphicsEnvironment;
import java.awt.Robot;

final class AwtRobotDriver implements RobotDriver {
    private final Robot robot;
    AwtRobotDriver() {
        if (GraphicsEnvironment.isHeadless()) throw new KuudraException("AWT Robot is unavailable in a headless environment");
        try { robot = new Robot(); robot.setAutoDelay(0); }
        catch (AWTException | SecurityException error) { throw KuudraException.wrap("Failed to initialize AWT Robot", error); }
    }
    @Override public void keyPress(int code) { robot.keyPress(code); }
    @Override public void keyRelease(int code) { robot.keyRelease(code); }
    @Override public void mousePress(int mask) { robot.mousePress(mask); }
    @Override public void mouseRelease(int mask) { robot.mouseRelease(mask); }
    @Override public void mouseMove(int x, int y) { robot.mouseMove(x, y); }
    @Override public void mouseWheel(int amount) { robot.mouseWheel(amount); }
}
