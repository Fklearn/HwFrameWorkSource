package com.android.server.accessibility;

import android.view.accessibility.AccessibilityEvent;
import java.util.function.BiConsumer;

/* compiled from: lambda */
public final /* synthetic */ class -$$Lambda$AccessibilityManagerService$BX2CMQr5jU9WhPYx7Aaae4zgxf4 implements BiConsumer {
    public static final /* synthetic */ -$$Lambda$AccessibilityManagerService$BX2CMQr5jU9WhPYx7Aaae4zgxf4 INSTANCE = new -$$Lambda$AccessibilityManagerService$BX2CMQr5jU9WhPYx7Aaae4zgxf4();

    private /* synthetic */ -$$Lambda$AccessibilityManagerService$BX2CMQr5jU9WhPYx7Aaae4zgxf4() {
    }

    public final void accept(Object obj, Object obj2) {
        ((AccessibilityManagerService) obj).sendAccessibilityEventToInputFilter((AccessibilityEvent) obj2);
    }
}
