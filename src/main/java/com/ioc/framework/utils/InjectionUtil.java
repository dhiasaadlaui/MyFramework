package com.ioc.framework.utils;

import static org.burningwave.core.assembler.StaticComponentContainer.Fields;

import java.util.*;

import org.burningwave.core.classes.FieldCriteria;
 import com.ioc.framework.core.MyFrameworkApplication;
import com.ioc.framework.core.annotations.*;

import java.lang.reflect.Field;

public class InjectionUtil {

	private InjectionUtil() {
		super();
	}


	public static void autowire(MyFrameworkApplication application, Class<?> classz, Object classInstance)
			throws InstantiationException, IllegalAccessException {
		Collection<Field> fields = Fields.findAllAndMakeThemAccessible(
			FieldCriteria.forEntireClassHierarchy().allThoseThatMatch(field ->
				field.isAnnotationPresent(Autowired.class)
			), 
			classz
		);
		for (Field field : fields) {
			String qualifier = field.isAnnotationPresent(Qualifier.class)
					? field.getAnnotation(Qualifier.class).value()
					: null;
			Object fieldInstance = application.getBeanInstance(field.getType(), field.getName(), qualifier);
			Fields.setDirect(classInstance, field, fieldInstance);
			autowire(application, fieldInstance.getClass(), fieldInstance);
		}
	}

}