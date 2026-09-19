# This app has no reflective dependency, so R8 full mode (the AGP default) needs no help.
# If this file starts growing, a dependency was added that probably should not have been.

# Room converters reach enums via valueOf(), which R8 cannot see.
-keepclassmembers enum com.fuelexpenselog.domain.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
