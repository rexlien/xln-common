package xln.common.utils;

public class Override {

    private boolean overridden;
    private Object overrideObj;

    public Override(boolean override, Object overrideObj) {
        this.overridden = override;
        this.overrideObj = overrideObj;
    }

    public Override() {
    }

    public boolean isOverridden() {
        return overridden;
    }

    public Override setOverridden(boolean overridden) {
        this.overridden = overridden;
        return this;
    }

    public Object getOverrideObj() {
        return overrideObj;
    }

    public Override setOverrideObj(Object overrideObj) {
        this.overrideObj = overrideObj;
        return this;
    }

}
