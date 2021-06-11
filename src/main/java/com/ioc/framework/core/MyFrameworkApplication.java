package com.ioc.framework.core;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.management.RuntimeErrorException;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.classes.CacheableSearchConfig;
import org.burningwave.core.classes.ClassCriteria;
import org.burningwave.core.classes.ClassHunter;
import org.burningwave.core.classes.ClassHunter.SearchResult;
import org.burningwave.core.classes.SearchConfig;
import com.ioc.framework.core.annotations.Component;
import com.ioc.framework.core.annotations.Service;
import com.ioc.framework.utils.InjectionUtil;


public class MyFrameworkApplication {
	private ConcurrentHashMap<Class<?>, Class<?>> implMap;
	private ConcurrentHashMap<Class<?>, Object> applicationScope;

	private static MyFrameworkApplication application;

	private MyFrameworkApplication() {
		super();
		implMap = new ConcurrentHashMap<>();
		applicationScope = new ConcurrentHashMap<>();
	}


	public static void startApplication(Class<?> mainClass) {
		try {
				if (application == null) {
					application = new MyFrameworkApplication();
					application.initFramework(mainClass);
				
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public static <T> T getComponent(Class<T> classz) {
		try {
			return application.getBeanInstance(classz);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}


	private void initFramework(Class<?> mainClass)
			throws InstantiationException, IllegalAccessException, ClassNotFoundException, IOException {
		Class<?>[] classes = getClasses(mainClass.getPackage().getName(), true);
		ComponentContainer componentConatiner = ComponentContainer.getInstance();
		ClassHunter classHunter = componentConatiner.getClassHunter();
		String packageRelPath = mainClass.getPackage().getName().replace(".", "/");
		try (SearchResult result = classHunter.findBy(
			//Highly optimized scanning by filtering resources before loading from ClassLoader
      			SearchConfig.forResources(
				packageRelPath
			).by(ClassCriteria.create().allThoseThatMatch(cls -> {
				return cls.getAnnotation(Component.class) != null || cls.getInterfaces().length > 0;
			}))
		)) {
			Collection<Class<?>> types = result.getClasses();
			for (Class<?> implementationClass : types) {
				Class<?>[] interfaces = implementationClass.getInterfaces();
				if (interfaces.length == 0 && implementationClass.getAnnotation(Component.class) !=null) {
					implMap.put(implementationClass, implementationClass);
				} else {
					for (Class<?> iface : interfaces) {
						if(iface.getAnnotation(Service.class)!=null)
						implMap.put(implementationClass, iface);
					}
				}
			}

			for (Class<?> classz : classes) {
				if (classz.isAnnotationPresent(Component.class)) {
					Object classInstance = classz.newInstance();
					applicationScope.put(classz, classInstance);
					InjectionUtil.autowire(this, classz, classInstance);
				}
			}
		};	

	}
	
	
	public Class<?>[] getClasses(String packageName, boolean recursive) throws ClassNotFoundException, IOException {
		ComponentContainer componentConatiner = ComponentContainer.getInstance();
		ClassHunter classHunter = componentConatiner.getClassHunter();
		String packageRelPath = packageName.replace(".", "/");
		//Highly optimized scanning by filtering resources before loading from ClassLoader
    CacheableSearchConfig config = SearchConfig.forResources(
			packageRelPath
		);
		if (!recursive) {
			config.notRecursiveOnPath(
				packageRelPath, false
			);
		}
		
		try (SearchResult result = classHunter.findBy(config)) {
			Collection<Class<?>> classes = result.getClasses();
			return classes.toArray(new Class[classes.size()]);
		}	
	}



	@SuppressWarnings("unchecked")
	private <T> T getBeanInstance(Class<T> interfaceClass) throws InstantiationException, IllegalAccessException {
		return (T) getBeanInstance(interfaceClass, null, null);
	}

	
	public <T> Object getBeanInstance(Class<T> interfaceClass, String fieldName, String qualifier)
			throws InstantiationException, IllegalAccessException {
		Class<?> implementationClass = getImplimentationClass(interfaceClass, fieldName, qualifier);

		if (applicationScope.containsKey(implementationClass)) {
			return applicationScope.get(implementationClass);
		}
			Object service = implementationClass.newInstance();
			applicationScope.put(implementationClass, service);
			return service;
		
	}


	private Class<?> getImplimentationClass(Class<?> interfaceClass, final String fieldName, final String qualifier) {
		Set<Entry<Class<?>, Class<?>>> implementationClasses = implMap.entrySet().stream()
				.filter(entry -> entry.getValue() == interfaceClass).collect(Collectors.toSet());
		String errorMessage = "";
		if (implementationClasses == null || implementationClasses.size() == 0) {
			errorMessage = "no implementation found for interface " + interfaceClass.getName();
		} else if (implementationClasses.size() == 1) {
			Optional<Entry<Class<?>, Class<?>>> optional = implementationClasses.stream().findFirst();
			if (optional.isPresent()) {
				return optional.get().getKey();
			}
		} else if (implementationClasses.size() > 1) {
			final String findBy = (qualifier == null || qualifier.trim().length() == 0) ? fieldName : qualifier;
			Optional<Entry<Class<?>, Class<?>>> optional = implementationClasses.stream()
					.filter(entry -> entry.getKey().getSimpleName().equalsIgnoreCase(findBy)).findAny();
			if (optional.isPresent()) {
				return optional.get().getKey();
			} else {
				errorMessage = "There are " + implementationClasses.size() + " of interface " + interfaceClass.getName()
						+ " Expected single implementation or make use of @CustomQualifier to resolve conflict";
			}
		}
		throw new RuntimeErrorException(new Error(errorMessage));
	}
}