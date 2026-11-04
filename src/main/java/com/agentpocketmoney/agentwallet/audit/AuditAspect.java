package com.agentpocketmoney.agentwallet.audit;

import com.agentpocketmoney.agentwallet.service.AuditService;
import java.lang.reflect.Method;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditAspect {
    private final AuditService audit;
    public AuditAspect(AuditService audit){this.audit=audit;}
    @Around("@annotation(audited)")
    public Object auditSuccess(ProceedingJoinPoint invocation,Audited audited) throws Throwable {
        Object result=invocation.proceed();
        UUID entityId=entityId(result);
        if(entityId==null) for(int i=invocation.getArgs().length-1;i>=0;i--)
            if(invocation.getArgs()[i] instanceof UUID id){entityId=id;break;}
        audit.record(audited.action(),audited.entityType(),entityId);
        return result;
    }
    private static UUID entityId(Object result){
        if(result==null)return null;
        if(result instanceof UUID id)return id;
        for(String methodName:new String[]{"getId","id"}) try {
            Method method=result.getClass().getMethod(methodName); Object value=method.invoke(result);
            if(value instanceof UUID id)return id;
        } catch(ReflectiveOperationException ignored) { /* try the next common identifier accessor */ }
        return null;
    }
}
