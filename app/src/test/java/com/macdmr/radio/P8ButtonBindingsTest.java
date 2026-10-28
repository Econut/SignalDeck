package com.macdmr.radio;
import org.junit.Test;
import static org.junit.Assert.*;
import android.view.KeyEvent;
public class P8ButtonBindingsTest {
    @Test public void reservedKeysCannotBecomeProgrammableKeys(){assertFalse(P8ButtonBindings.acceptable(KeyEvent.KEYCODE_POWER));assertFalse(P8ButtonBindings.acceptable(KeyEvent.KEYCODE_HOME));assertFalse(P8ButtonBindings.acceptable(KeyEvent.KEYCODE_BACK));assertFalse(P8ButtonBindings.acceptable(KeyEvent.KEYCODE_VOLUME_UP));assertFalse(P8ButtonBindings.acceptable(KeyEvent.KEYCODE_VOLUME_DOWN));}
    @Test public void p8BottomLeftCameraKeyCanBeLearned(){assertTrue(P8ButtonBindings.acceptable(KeyEvent.KEYCODE_CAMERA));assertFalse(P8ButtonBindings.acceptable(KeyEvent.KEYCODE_FOCUS));}
    @Test public void functionAndGamepadKeysCanBeLearned(){assertTrue(P8ButtonBindings.acceptable(KeyEvent.KEYCODE_F1));assertTrue(P8ButtonBindings.acceptable(KeyEvent.KEYCODE_F12));assertTrue(P8ButtonBindings.acceptable(KeyEvent.KEYCODE_BUTTON_A));}
}
