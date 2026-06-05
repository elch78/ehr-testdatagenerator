package be.elchworks.testdatagenerator.codegen;

import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compiles Java source held in a string into a loaded {@link Class}, without touching the file system.
 */
final class InMemoryCompiler {

    private InMemoryCompiler() {
    }

    static Class<?> compile(String qualifiedName, String code) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No system Java compiler available; run on a JDK, not a JRE");
        }

        ClassCollectingFileManager fileManager = new ClassCollectingFileManager(compiler.getStandardFileManager(null, null, null));
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        boolean success = compiler
                .getTask(null, fileManager, diagnostics, null, null, List.of(new StringSource(qualifiedName, code)))
                .call();
        if (!success) {
            throw new IllegalStateException("Compilation failed: " + diagnostics.getDiagnostics());
        }

        return fileManager.loadCompiledClass(qualifiedName);
    }

    private static final class StringSource extends SimpleJavaFileObject {
        private final String code;

        StringSource(String qualifiedName, String code) {
            super(URI.create("string:///" + qualifiedName.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }

    private static final class CompiledClass extends SimpleJavaFileObject {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        CompiledClass(String qualifiedName) {
            super(URI.create("bytes:///" + qualifiedName.replace('.', '/') + Kind.CLASS.extension), Kind.CLASS);
        }

        @Override
        public OutputStream openOutputStream() {
            return bytes;
        }

        byte[] bytes() {
            return bytes.toByteArray();
        }
    }

    private static final class ClassCollectingFileManager extends ForwardingJavaFileManager<JavaFileManager> {
        private final Map<String, CompiledClass> compiled = new HashMap<>();

        ClassCollectingFileManager(JavaFileManager delegate) {
            super(delegate);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
            CompiledClass compiledClass = new CompiledClass(className);
            compiled.put(className, compiledClass);
            return compiledClass;
        }

        Class<?> loadCompiledClass(String qualifiedName) {
            ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
                @Override
                protected Class<?> findClass(String name) throws ClassNotFoundException {
                    CompiledClass compiledClass = compiled.get(name);
                    if (compiledClass == null) {
                        throw new ClassNotFoundException(name);
                    }
                    byte[] bytes = compiledClass.bytes();
                    return defineClass(name, bytes, 0, bytes.length);
                }
            };
            try {
                return loader.loadClass(qualifiedName);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
