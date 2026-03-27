package org.lognet.springboot.grpc.demo;

import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Created by 310242212 on 01-Nov-16.
 */

@Aspect
@Component
@Profile(value = {"aopTest"})
public class AopServiceMonitor {
    private static final Logger log = LoggerFactory.getLogger(AopServiceMonitor.class);

    public AopServiceMonitor() {
    }

    //@AfterReturning("execution(* org.lognet..*Service.*(..))")
    @After("execution(* org.lognet..*Service.*(..))")
    public void afterLogServiceAccess( ) {

        log.info("Hi from AOP. - after");
    }

    @Before("execution(* org.lognet..*Service.*(..))")
    public void beforeLogServiceAccess( ) {
        log.info("Hi from AOP. - before");
    }






}