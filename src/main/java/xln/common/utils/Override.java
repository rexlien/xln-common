package xln.common.utils;

public class Override {

    private boolean overridden;
    private Object object;

    public Override(boolean override, Object object) {
        this.overridden = override;
        this.object = object;
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

    public Object getObject() {
        return object;
    }

    public Override setObject(Object object) {
        this.object = object;
        return this;
    }
}
