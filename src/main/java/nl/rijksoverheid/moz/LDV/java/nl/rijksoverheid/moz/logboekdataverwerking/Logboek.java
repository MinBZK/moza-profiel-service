package LDV.java.nl.rijksoverheid.moz.logboekdataverwerking;


import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@InterceptorBinding
@Target({TYPE, METHOD})
@Retention(RUNTIME)
public @interface Logboek {

    @Nonbinding
    String name() default "";
    
    @Nonbinding
    String processingActivityId() default "";

}
