package io.sapl.spring.method.blocking;

import java.lang.annotation.Annotation;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.Pointcut;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.Pointcuts;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.core.Ordered;

import io.sapl.spring.method.metadata.EnforceDropWhileDenied;
import io.sapl.spring.method.metadata.EnforceRecoverableIfDenied;
import io.sapl.spring.method.metadata.EnforceTillDenied;
import io.sapl.spring.method.metadata.PostEnforce;
import io.sapl.spring.method.metadata.PreEnforce;
import io.sapl.spring.method.reactive.ReactiveSaplMethodInterceptor;

public class PolicyEnforcementPointAroundMethodInterceptor
		implements Ordered, MethodInterceptor, PointcutAdvisor, AopInfrastructureBean {

	private final Pointcut          pointcut;
	private final int               order;
	private final MethodInterceptor policyEnforcementPoint;

	public static PolicyEnforcementPointAroundMethodInterceptor preEnforce(MethodInterceptor policyEnforcementPoint) {
		return new PolicyEnforcementPointAroundMethodInterceptor(PreEnforce.class,
				SaplAuthorizationInterceptorsOrder.PRE_ENFORCE.getOrder(), policyEnforcementPoint);
	}

	public static PolicyEnforcementPointAroundMethodInterceptor postEnforce(MethodInterceptor policyEnforcementPoint) {
		return new PolicyEnforcementPointAroundMethodInterceptor(PostEnforce.class,
				SaplAuthorizationInterceptorsOrder.POST_ENFORCE.getOrder(), policyEnforcementPoint);
	}

	public static PolicyEnforcementPointAroundMethodInterceptor reactive(
			ReactiveSaplMethodInterceptor policyEnforcementPoint) {
		return new PolicyEnforcementPointAroundMethodInterceptor(
				SaplAuthorizationInterceptorsOrder.PRE_ENFORCE.getOrder(), policyEnforcementPoint);
	}

	PolicyEnforcementPointAroundMethodInterceptor(int order,
			MethodInterceptor policyEnforcementPoint) {
		this.pointcut               = pointcutForAllAnnotations();
		this.order                  = order;
		this.policyEnforcementPoint = policyEnforcementPoint;
	}

	PolicyEnforcementPointAroundMethodInterceptor(Class<? extends Annotation> annotation, int order,
			MethodInterceptor policyEnforcementPoint) {
		this.pointcut               = pointcutForAnnotation(annotation);
		this.order                  = order;
		this.policyEnforcementPoint = policyEnforcementPoint;
	}

	private static Pointcut pointcutForAnnotation(Class<? extends Annotation> annotation) {
		return new ComposablePointcut(classOrMethod(annotation));

	}

	private static Pointcut pointcutForAllAnnotations() {
		var cut = new ComposablePointcut(classOrMethod(PreEnforce.class));
		cut = cut.union(classOrMethod(PostEnforce.class));
		cut = cut.union(classOrMethod(EnforceRecoverableIfDenied.class));
		cut = cut.union(classOrMethod(EnforceDropWhileDenied.class));
		return cut.union(classOrMethod(EnforceTillDenied.class));
	}

	private static Pointcut classOrMethod(Class<? extends Annotation> annotation) {
		return Pointcuts.union(new AnnotationMatchingPointcut(null, annotation, true),
				new AnnotationMatchingPointcut(annotation, true));
	}

	@Override
	public Advice getAdvice() {
		return this;
	}

	@Override
	public boolean isPerInstance() {
		return false;
	}

	@Override
	public Pointcut getPointcut() {
		return pointcut;
	}

	@Override
	public int getOrder() {
		return order;
	}

	@Override
	public Object invoke(MethodInvocation invocation) throws Throwable {
		return policyEnforcementPoint.invoke(invocation);
	}
}
