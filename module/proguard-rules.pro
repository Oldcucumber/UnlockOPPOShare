# LSPosed loads this entry by its name in assets/xposed_init.
-keep class com.oldcucumber.oppocastunlock.CastUnlockHook { *; }
# AndroidViewModelFactory instantiates the model through this constructor.
-keepclassmembers class com.oldcucumber.oppocastunlock.AppSelectionViewModel {
    public <init>(android.app.Application);
}
