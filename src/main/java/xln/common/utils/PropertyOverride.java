package xln.common.utils;

public class PropertyOverride {

    private boolean overridden;
    private Object overrideObj;

    public PropertyOverride(boolean override, Object overrideObj) {
        this.overridden = override;
        this.overrideObj = overrideObj;
    }

    public PropertyOverride() {
    }

    public boolean isOverridden() {
        return overridden;
    }

    public PropertyOverride setOverridden(boolean overridden) {
        this.overridden = overridden;
        return this;
    }

    public Object getOverrideObj() {
        return overrideObj;
    }

    public PropertyOverride setOverrideObj(Object overrideObj) {
        this.overrideObj = overrideObj;
        return this;
    }

}
