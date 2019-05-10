package xln.common.expression;

import lombok.Data;

@Data
public class Condition implements Element{
    private String srcPath;
    private int op;
    private Object target;
}
