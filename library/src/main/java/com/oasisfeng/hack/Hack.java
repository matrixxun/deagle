package com.oasisfeng.hack;

import android.support.annotation.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** @author Oasis */
public class Hack {

	/** All hacks should be declared in a centralized point extending this class, typically as static
	 * method, and call it in your application initialization stage to verify all the hack
	 * assertions by catching exception thrown:
	 * <pre>
	 * class MyHacks extends HackDeclaration {
	 *
	 *     static HackedField<Object, PackageManager> ContextImpl_mPackageManager;
	 *     static HackedField<Object, Map<String, IBinder>> ServiceManager_sCache;
	 *
	 *     static void defineAndVerify() {
	 *         try {
	 *             ContextImpl_mPackageManager = Hack.into("android.app.ContextImpl").field("mPackageManager").ofType(PackageManager.class);
	 *             ServiceManager_sCache = Hack.into("android.os.ServiceManager").staticField("sCache").ofGenericType(Map.class)
	 *             
	 *             ...
	 *         } catch (HackAssertionException e) {
	 *             // Report the failure and activate fall-back strategy.
	 *             ...
	 *         }
	 *     }
	 * }
	 * <pre>
	 * Thus we can verify them all together in an early application initialization stage. If any assertion
	 * failed, a fall-back strategy is suggested. */
	public static abstract class HackDeclaration {

		/** This exception is purposely defined as "protected" and not extending Exception to avoid
		 * developers unconsciously catch it outside the centralized hacks declaration, which results
		 * in potentially pre-checked usage of hacks. */
		public static class HackAssertionException extends Throwable {

			private Class<?> clazz;
			private String mHackedFieldName;			
			private String mHackedMethodName;
	
			public HackAssertionException(final String e) { super(e); }
			public HackAssertionException(final Exception e) { super(e); }

			@Override public String toString() {
				return getCause() != null ? getClass().getName() + ": " + getCause() : super.toString();
			}			
			
			public Class<?> getHackedClass() {
				return clazz;
			}
			
			public HackAssertionException setHackedClass(final Class<?> hacked_class) {
				clazz = hacked_class; return this;
			}		
			
			public String getHackedMethodName() {
				return mHackedMethodName;
			}
			
			public HackAssertionException setHackedMethodName(final String method) {
				this.mHackedMethodName = method; return this;
			}
			
			public String getHackedFieldName() {
				return mHackedFieldName;
			}
			
			public HackAssertionException setHackedFieldName(final String field) {
				this.mHackedFieldName = field; return this;
			}

			private static final long serialVersionUID = 1L;
		}
	}

	/** Use {@link Hack#setAssertionFailureHandler(AssertionFailureHandler) } to set the global handler */
	public interface AssertionFailureHandler {
		
		/** @return whether the failure is handled and no need to throw out, return false to let it thrown */
		boolean onAssertionFailure(HackDeclaration.HackAssertionException failure);
	}

	public static class HackedField<C, T> {

		/** Assert the field type */
		@SuppressWarnings("unchecked")
		public <T2> HackedField<C, T2> ofType(final Class<T2> type) throws HackDeclaration.HackAssertionException {
			if (mField != null && ! type.isAssignableFrom(mField.getType()))
				fail(new HackDeclaration.HackAssertionException(new ClassCastException(mField + " is not of type " + type)));
			return (HackedField<C, T2>) this;
		}

		@SuppressWarnings("unchecked")
		public HackedField<C, T> ofType(final String type_name) throws HackDeclaration.HackAssertionException {
			try {
				return (HackedField<C, T>) ofType(Class.forName(type_name, false, mField.getDeclaringClass().getClassLoader()));
			} catch (final ClassNotFoundException e) {
				fail(new HackDeclaration.HackAssertionException(e)); return this;
			}
		}

		public HackedTargetField<T> on(final C target) {
			if (target == null) throw new IllegalArgumentException("target is null");
			return onTarget(target);
		}

		HackedTargetField<T> onTarget(final @Nullable C target) { return new HackedTargetField<>(mField, target); }

		/** Get current value of this field */
		public T get(final C instance) {
			try {
				@SuppressWarnings("unchecked") final T value = (T) mField.get(instance);
				return value;
			} catch (final IllegalAccessException e) {
				e.printStackTrace();
				return null; /* Should never happen */ }
		}

		/**
		 * Set value of this field
		 * 
		 * <p>No type enforced here since most type mismatch can be easily tested and exposed early.</p>
		 */
		public void set(final C instance,final Object value) {
			try {
				mField.set(instance, value);
			} catch (final IllegalAccessException ignored) {}	// Should never happen
		}

		/** @param modifiers the modifiers this field must have */
		HackedField(final Class<C> clazz, final String name, final int modifiers) throws HackDeclaration.HackAssertionException {
			Field field = null;
			try {
				if (clazz == null) return;
				field = clazz.getDeclaredField(name);
				if (Modifier.isStatic(modifiers) != Modifier.isStatic(field.getModifiers()))
					fail(new HackDeclaration.HackAssertionException(field + (Modifier.isStatic(modifiers) ? " is not static" : "is static")).setHackedFieldName(name));
				if (modifiers > 0 && (field.getModifiers() & modifiers) != modifiers)
					fail(new HackDeclaration.HackAssertionException(field + " does not match modifiers: " + modifiers).setHackedFieldName(name));
				if (! field.isAccessible()) field.setAccessible(true);
			} catch (final NoSuchFieldException e) {
				final HackDeclaration.HackAssertionException hae = new HackDeclaration.HackAssertionException(e);
				hae.setHackedClass(clazz);
				hae.setHackedFieldName(name);
				fail(hae);
			} finally { mField = field; }
		}
		private final Field mField;
		
		public Field getField() {
			return mField;
		}
	}

	public static class HackedTargetField<T> {

		public T get() {
			try {
				@SuppressWarnings("unchecked") final T value = (T) mField.get(mInstance);
				return value;
			} catch (final IllegalAccessException e) { return null; }	// Should never happen
		}

		public void set(final T value) {
			try {
				mField.set(mInstance, value);
			} catch (final IllegalAccessException ignored) {}			// Should never happen
		}

		HackedTargetField(final Field field, final @Nullable Object instance) {
			mField = field;
			mInstance = instance;
		}

		private final Field mField;
		private final Object mInstance;		// Instance type is already checked
	}

	public interface HackedUnsafeMethod<C> {

		<R> HackedMethod<R, C> returning(Class<R> return_type) throws HackDeclaration.HackAssertionException;
	}

	public interface HackedMethod<R, C> {
		HackedTargetMethod<R> on(C target);
	}

	public interface HackedTargetMethod<R> {
		R invoke(Object... args) throws IllegalArgumentException, InvocationTargetException;
	}

	public interface HackedMethod1<R, C, A1> { R invoke(C target, A1 arg) throws InvocationTargetException; }
	public interface HackedMethod2<R, C, A1, A2> { R invoke(C target, A1 arg1, A2 arg2) throws InvocationTargetException; }
	public interface HackedMethod3<R, C, A1, A2, A3> { R invoke(C target, A1 arg1, A2 arg2, A3 arg3) throws InvocationTargetException; }
	public interface HackedMethod4<R, C, A1, A2, A3, A4> { R invoke(C target, A1 arg1, A2 arg2, A3 arg3, A4 arg4) throws InvocationTargetException; }

	private static class HackedMethodImplBase<C, R> {

		protected R invoke(final C target, final Object... args) throws IllegalArgumentException, InvocationTargetException {
			try {
				@SuppressWarnings("unchecked") final R obj = (R) mMethod.invoke(target, args);
				return obj;
			} catch (final IllegalAccessException e) { return null; }	// Should never happen
		}

		protected HackedTargetMethod<R> onTarget(final @Nullable C target) {
			return new HackedTargetMethodImpl<>(mMethod, target);
		}

		HackedMethodImplBase(final Class<C> clazz, final String name, final int modifiers, final Class<?>... arg_types) throws HackDeclaration.HackAssertionException {
			Method method = null;
			try {
				if (clazz == null) return;
				method = clazz.getDeclaredMethod(name, arg_types);
				if (Modifier.isStatic(modifiers) != Modifier.isStatic(method.getModifiers()))
					fail(new HackDeclaration.HackAssertionException(method + (Modifier.isStatic(modifiers) ? " is not static" : "is static")).setHackedMethodName(name));
				if (modifiers > 0 && (method.getModifiers() & modifiers) != modifiers)
					fail(new HackDeclaration.HackAssertionException(method + " does not match modifiers: " + modifiers).setHackedMethodName(name));
				if (! method.isAccessible()) method.setAccessible(true);
			} catch (final NoSuchMethodException e) {
				final HackDeclaration.HackAssertionException hae = new HackDeclaration.HackAssertionException(e);
				hae.setHackedClass(clazz);
				hae.setHackedMethodName(name);
				fail(hae);
			} finally { mMethod = method; }
		}

		protected final Method mMethod;

		public Method getMethod() {
			return mMethod;
		}		
	}

	private static class HackedMethodImpl<R, C> extends HackedMethodImplBase<C, R> implements HackedMethod<R, C> {

		HackedMethodImpl(final Class<C> clazz, final String name, final int modifiers, final Class<?>... arg_types) throws HackDeclaration.HackAssertionException {
			super(clazz, name, modifiers, arg_types);
		}

		@Override public R invoke(final C target, final Object... args) throws IllegalArgumentException, InvocationTargetException {
			return super.invoke(target, args);
		}

		@Override public HackedTargetMethod<R> on(final C target) {
			if (target == null) throw new IllegalArgumentException("target is null");
			return onTarget(target);
		}
	}

	private static class HackedMethod1Impl<C, R, A1> extends HackedMethodImplBase<C, R> implements HackedMethod1<R, C, A1> {

		@Override public R invoke(final C target, final A1 arg) throws InvocationTargetException {
			return super.invoke(target, arg);
		}

		HackedMethod1Impl(final Class<C> clazz, final String name, final int modifiers, final Class<A1> arg_type) throws HackDeclaration.HackAssertionException {
			super(clazz, name, modifiers, arg_type);
		}
	}

	private static class HackedMethod2Impl<C, R, A1, A2> extends HackedMethodImplBase<C, R> implements HackedMethod2<R, C, A1, A2> {

		@Override public R invoke(final C target, final A1 arg1, final A2 arg2) throws InvocationTargetException {
			return super.invoke(target, arg1, arg2);
		}

		HackedMethod2Impl(final Class<C> clazz, final String name, final int modifiers, final Class<A1> arg1_type, final Class<A2> arg2_type) throws HackDeclaration.HackAssertionException {
			super(clazz, name, modifiers, arg1_type, arg2_type);
		}
	}

	private static class HackedMethod3Impl<C, R, A1, A2, A3> extends HackedMethodImplBase<C, R> implements HackedMethod3<R, C, A1, A2, A3> {

		@Override public R invoke(final C target, final A1 arg1, final A2 arg2, final A3 arg3) throws InvocationTargetException {
			return super.invoke(target, arg1, arg2, arg3);
		}

		HackedMethod3Impl(final Class<C> clazz, final String name, final int modifiers, final Class<A1> arg1_type, final Class<A2> arg2_type, final Class<A3> arg3_type) throws HackDeclaration.HackAssertionException {
			super(clazz, name, modifiers, arg1_type, arg2_type, arg3_type);
		}
	}

	private static class HackedMethod4Impl<C, R, A1, A2, A3, A4> extends HackedMethodImplBase<C, R> implements HackedMethod4<R, C, A1, A2, A3, A4> {

		@Override public R invoke(final C target, final A1 arg1, final A2 arg2, final A3 arg3, final A4 arg4) throws InvocationTargetException {
			return super.invoke(target, arg1, arg2, arg3, arg4);
		}

		HackedMethod4Impl(final Class<C> clazz, final String name, final int modifiers, final Class<A1> arg1_type, final Class<A2> arg2_type, final Class<A3> arg3_type, final Class<A4> arg4_type) throws HackDeclaration.HackAssertionException {
			super(clazz, name, modifiers, arg1_type, arg2_type, arg3_type, arg4_type);
		}
	}

	private static class HackedTargetMethodImplBase<R> {

		HackedTargetMethodImplBase(final Method mMethod, final Object mTarget) { this.mMethod = mMethod; this.mTarget = mTarget; }

		protected R invoke(final Object... args) throws IllegalArgumentException, InvocationTargetException {
			try {
				@SuppressWarnings("unchecked") final R result = (R) mMethod.invoke(mTarget, args);
				return result;
			} catch (final IllegalAccessException e) { return null; }	// Should never happen
		}

		protected final Method mMethod;
		protected final Object mTarget;
	}

	private static class HackedTargetMethodImpl<R> extends HackedTargetMethodImplBase<R> implements HackedTargetMethod<R> {

		HackedTargetMethodImpl(final Method method, final Object target) { super(method, target); }

		@Override public R invoke(final Object... args) throws IllegalArgumentException, InvocationTargetException {
			return super.invoke(args);
		}
	}

	public static class HackedConstructor {

		protected Constructor<?> mConstructor;

		HackedConstructor(final Class<?> clazz, final Class<?>[] arg_types) throws HackDeclaration.HackAssertionException {
			try {
				if (clazz == null) return;
				mConstructor = clazz.getDeclaredConstructor(arg_types);
			} catch (final NoSuchMethodException e) {
				final HackDeclaration.HackAssertionException hae = new HackDeclaration.HackAssertionException(e);
				hae.setHackedClass(clazz);
				fail(hae);
			}
		}

		public Object getInstance(final Object... arg_types) throws IllegalArgumentException, InvocationTargetException {
			try {
				mConstructor.setAccessible(true);
				return mConstructor.newInstance(arg_types);
			} catch (final Exception e) {
				throw new InvocationTargetException(e);
			}
		}
	}
	
	public static class HackedClass<C> {

		public <T> HackedField<C, T> field(final String name) throws HackDeclaration.HackAssertionException {
			return new HackedField<C, T>(mClass, name, 0) {};
		}

		public <T> HackedTargetField<T> staticField(final String name) throws HackDeclaration.HackAssertionException {
			return new HackedField<C, T>(mClass, name, Modifier.STATIC).onTarget(null);
		}

		public HackedUnsafeMethod<C> method(final String name, final Class<?>... arg_types) {
			return new HackedUnsafeMethod<C>() {
				@Override public <R> HackedMethod<R, C> returning(final Class<R> return_type) throws HackDeclaration.HackAssertionException {
					return new HackedMethodImpl<>(mClass, name, 0, arg_types);
				}
			};
		}

		public <R, A1> HackedMethod1<R, C, A1> method(final String name, final Class<A1> arg1_type) throws HackDeclaration.HackAssertionException {
			return new HackedMethod1Impl<>(mClass, name, 0, arg1_type);
		}

		public <R, A1, A2> HackedMethod2<R, C, A1, A2> method(final String name, final Class<A1> arg1_type, final Class<A2> arg2_type) throws HackDeclaration.HackAssertionException {
			return new HackedMethod2Impl<>(mClass, name, 0, arg1_type, arg2_type);
		}

		public <R, A1, A2, A3> HackedMethod3<R, C, A1, A2, A3> method(final String name, final Class<A1> arg1_type, final Class<A2> arg2_type, final Class<A3> arg3_type) throws HackDeclaration.HackAssertionException {
			return new HackedMethod3Impl<>(mClass, name, 0, arg1_type, arg2_type, arg3_type);
		}

		public <R, A1, A2, A3, A4> HackedMethod4<R, C, A1, A2, A3, A4> method(final String name, final Class<A1> arg1_type, final Class<A2> arg2_type, final Class<A3> arg3_type, final Class<A4> arg4_type) throws HackDeclaration.HackAssertionException {
			return new HackedMethod4Impl<>(mClass, name, 0, arg1_type, arg2_type, arg3_type, arg4_type);
		}

		public <R> HackedTargetMethod<R> staticMethod(final String name, final Class<?>... arg_types) throws HackDeclaration.HackAssertionException {
			return new HackedMethodImpl<R, C>(mClass, name, Modifier.STATIC, arg_types).onTarget(null);
		}

		public HackedConstructor constructor(final Class<?>... arg_types) throws HackDeclaration.HackAssertionException {
			return new HackedConstructor(mClass, arg_types);
		}
		
		public HackedClass(final Class<C> clazz) { mClass = clazz; }

		protected Class<C> mClass;
	}

	public static <T> HackedClass<T> into(final Class<T> clazz) {
		return new HackedClass<>(clazz);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static <T> HackedClass<T> into(final String class_name) throws HackDeclaration.HackAssertionException {
		try {
			return new HackedClass(Class.forName(class_name));
		} catch (final ClassNotFoundException e) {
			fail(new HackDeclaration.HackAssertionException(e));
			return new HackedClass(null);	// TODO: Better solution to avoid null?
		}
	}
	
	private static void fail(final HackDeclaration.HackAssertionException e) throws HackDeclaration.HackAssertionException {
		if (sFailureHandler == null || ! sFailureHandler.onAssertionFailure(e)) throw e;
	}

	/** Specify a handler to deal with assertion failure, and decide whether the failure should be thrown. */
	public static void setAssertionFailureHandler(final AssertionFailureHandler handler) {
		sFailureHandler = handler;
	}

	private Hack() {}

	private static AssertionFailureHandler sFailureHandler;
}
