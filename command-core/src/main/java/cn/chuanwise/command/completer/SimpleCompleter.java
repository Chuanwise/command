package cn.chuanwise.command.completer;

import cn.chuanwise.command.context.CompleteContext;
import cn.chuanwise.command.Priority;
import cn.chuanwise.common.api.ExceptionFunction;
import cn.chuanwise.common.util.Preconditions;
import lombok.Data;

import java.util.Set;

/**
 * 简单解析器
 *
 * @author Chuanwise
 */
@Data
@SuppressWarnings("all")
public class SimpleCompleter
    extends AbstractCompleter {
    
    private final ExceptionFunction<CompleteContext, Set<String>> action;
    
    public SimpleCompleter(Class<?> completedClass, ExceptionFunction<CompleteContext, Set<String>> action) {
        super(completedClass);
    
        Preconditions.objectNonNull(action, "action");
        
        this.action = action;
    }
    
    @Override
    protected Set<String> complete0(CompleteContext context) throws Exception {
        return action.exceptApply(context);
    }
}
