package xln.common.expression;


import java.util.LinkedList;
import java.util.List;

public class Result {

    public static class Progress {
        public Object getCurrent() {
            return current;
        }

        public Progress setCurrent(Object current) {
            this.current = current;
            return this;
        }

        private Object current;

        public Object getTarget() {
            return target;
        }

        public Progress setTarget(Object target) {
            this.target = target;
            return this;
        }

        private Object target;

        public Progress(Object current, Object target) {
            this.current = current;
            this.target = target;
        }

    }

    public Result(Boolean result) {
        this.result = result;
    }

    public Result(Boolean result, Progress progress) {
        this.result = result;
        this.progress = progress;
    }

    public Result() {

    }

    public Boolean getResult() {
        return result;
    }

    public Result setResult(Boolean result) {
        this.result = result;
        return this;
    }

    private Boolean result;

    public Progress getProgress() {
        return progress;
    }

    private Progress progress;

    public String getTag() {
        return tag;
    }

    public Result setTag(String tag) {
        this.tag = tag;
        return this;
    }

    private String tag;

    public List<Result> getChildren() {
        return children;
    }

    private List<Result> children = new LinkedList<>();

    public void subResult(Result sub) {

        children.add(sub);

    }
    public void progress(long current, long target) {
        if (progress != null) {
            this.progress.current = current;
            this.progress.target = target;

        }
    }
}








