/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import static org.junit.Assert.*;

import org.junit.Test;

/** The engine interface: the DMR engine keeps everything it had, and says so. */
public class ModuleEngineTest {
    @Test public void dmrEngineIsAModuleEngine() {
        assertTrue(ModuleEngine.class.isAssignableFrom(RadioEngine.class));
        ModuleEngine plain = (ModuleEngine) java.lang.reflect.Proxy.newProxyInstance(ModuleEngine.class.getClassLoader(), new Class<?>[] { ModuleEngine.class },
                (p, m, a) -> m.isDefault() ? java.lang.invoke.MethodHandles.privateLookupIn(ModuleEngine.class, java.lang.invoke.MethodHandles.lookup())
                        .unreflectSpecial(m, ModuleEngine.class).bindTo(p).invokeWithArguments(a == null ? new Object[0] : a) : null);
        assertEquals("mcu68", plain.protocol());     // an engine that does not say otherwise is the DMR module
        assertTrue(plain.dmr());
    }
}
