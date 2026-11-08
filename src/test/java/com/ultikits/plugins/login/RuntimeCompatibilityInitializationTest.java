package com.ultikits.plugins.login;

import com.ultikits.ultitools.context.ComponentScanner;
import com.ultikits.ultitools.context.SimpleContainer;
import net.bytebuddy.jar.asm.AnnotationVisitor;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** Scan/load safety is distinct from executing an affected call; the positive control pins that distinction. */
class RuntimeCompatibilityInitializationTest {
    private static final String BASE = "com.ultikits.plugins.login";
    private static final String VIEW = "org/bukkit/inventory/InventoryView";

    @Test
    void frameworkDiscoveryLoadsEveryModuleClassWithOldInventoryViewShape(@TempDir Path stubs) throws Exception {
        ClassWriter oldView = new ClassWriter(0);
        oldView.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, VIEW, null, "java/lang/Object", null);
        oldView.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "getTopInventory",
                "()Lorg/bukkit/inventory/Inventory;", null, null).visitEnd();
        oldView.visitEnd();
        Path file = stubs.resolve(VIEW + ".class");
        Files.createDirectories(file.getParent());
        Files.write(file, oldView.toByteArray());

        ClassWriter probe = new ClassWriter(0);
        probe.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "compatprobe/Invoke", null, "java/lang/Object", null);
        MethodVisitor call = probe.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "call",
                "(L" + VIEW + ";)Lorg/bukkit/inventory/Inventory;", null, null);
        call.visitCode();
        call.visitVarInsn(Opcodes.ALOAD, 0);
        call.visitMethodInsn(Opcodes.INVOKEINTERFACE, VIEW, "getTopInventory",
                "()Lorg/bukkit/inventory/Inventory;", true);
        call.visitInsn(Opcodes.ARETURN);
        call.visitMaxs(1, 1);
        call.visitEnd();
        probe.visitEnd();
        Path probeFile = stubs.resolve("compatprobe/Invoke.class");
        Files.createDirectories(probeFile.getParent());
        Files.write(probeFile, probe.toByteArray());

        Path classes = Paths.get(UltiLogin.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Set<String> expected;
        try (Stream<Path> paths = Files.walk(classes.resolve(BASE.replace('.', '/')))) {
            expected = paths.filter(p -> p.toString().endsWith(".class"))
                    .map(p -> classes.relativize(p).toString().replace('/', '.').replace(".class", ""))
                    .collect(Collectors.toSet());
        }
        List<LogRecord> warnings = new ArrayList<>();
        Handler handler = new Handler() {
            @Override public void publish(LogRecord record) { warnings.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        Logger logger = Logger.getLogger(ComponentScanner.class.getName());
        logger.addHandler(handler);
        try (OldShapeLoader loader = new OldShapeLoader(new URL[]{stubs.toUri().toURL(), classes.toUri().toURL()})) {
            Class<?> shape = loader.loadClass(VIEW.replace('/', '.'));
            assertThat(shape.isInterface()).isFalse();
            // Positive control: an interface call really fails against this exact class shape.
            Class<?> invoke = loader.loadClass("compatprobe.Invoke");
            assertThatThrownBy(() -> invoke.getMethod("call", shape).invoke(null, new Object[]{null}))
                    .isInstanceOf(InvocationTargetException.class).hasCauseInstanceOf(IncompatibleClassChangeError.class);

            SimpleContainer container = mock(SimpleContainer.class);
            when(container.getClassLoader()).thenReturn(loader);
            new ComponentScanner(container).scanPackages(BASE);
            assertThat(expected).hasSizeGreaterThan(30);
            assertThat(loader.loaded).containsAll(expected);
            assertThat(loader.loaded).contains(BASE + ".UltiLogin", BASE + ".service.LoginService",
                    BASE + ".gui.RegisterGUIPage", BASE + ".listener.LoginProtectionListener");
            assertThat(warnings).noneSatisfy(record -> {
                assertThat(record.getLevel().intValue()).isGreaterThanOrEqualTo(java.util.logging.Level.WARNING.intValue());
            });
            // Discovery registers definitions only; no module service constructor/hook executes here.
            verify(container, never()).refresh();
        } finally {
            logger.removeHandler(handler);
        }
    }

    @Test
    void initializationCallGraphCannotReachAnyInventoryViewCall() throws Exception {
        Path classes = Paths.get(UltiLogin.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Map<String, Node> graph = new HashMap<>();
        List<Path> files;
        try (Stream<Path> paths = Files.walk(classes.resolve(BASE.replace('.', '/')))) {
            files = paths.filter(p -> p.toString().endsWith(".class")).collect(Collectors.toList());
        }
        for (Path file : files) {
            new ClassReader(Files.readAllBytes(file)).accept(new ClassVisitor(Opcodes.ASM9) {
                private String owner;
                @Override public void visit(int version, int access, String name, String signature, String parent, String[] interfaces) {
                    owner = name;
                }
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                           String signature, String[] exceptions) {
                    Node node = new Node();
                    node.root = name.equals("<init>") || name.equals("<clinit>") || owner.contains("/config/");
                    graph.put(owner + "." + name + descriptor, node);
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                            if (descriptor.equals("Lcom/ultikits/ultitools/annotations/PostConstruct;")) {
                                node.root = true;
                            }
                            return null;
                        }
                        @Override public void visitMethodInsn(int opcode, String target, String name, String descriptor, boolean isInterface) {
                            node.calls.add(target + "." + name + descriptor);
                            if (target.equals(VIEW)) { node.affected = true; }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        // Positive controls: check the real compiled product, not a source grep or an error-stub JAR.
        assertThat(graph.entrySet().stream().filter(e -> e.getValue().affected).map(Map.Entry::getKey))
                .hasSize(3).anyMatch(key -> key.contains("RegisterGUIPage"))
                .anyMatch(key -> key.contains("LoginProtectionListener"))
                .anyMatch(key -> key.contains("LoginService"));
        ArrayDeque<String> pending = new ArrayDeque<>();
        graph.forEach((name, node) -> { if (node.root) { pending.add(name); } });
        assertThat(pending).anyMatch(key -> key.contains("LoginService.<init>"))
                .anyMatch(key -> key.contains("LoginConfig.<init>"));
        Set<String> visited = new HashSet<>();
        while (!pending.isEmpty()) {
            String name = pending.remove();
            Node node = graph.get(name);
            if (node == null || !visited.add(name)) { continue; }
            assertThat(node.affected).as("reachable before enable: %s", name).isFalse();
            pending.addAll(node.calls);
        }
    }

    private static final class Node {
        boolean root;
        boolean affected;
        final Set<String> calls = new LinkedHashSet<>();
    }

    private static final class OldShapeLoader extends URLClassLoader {
        final Set<String> loaded = new HashSet<>();
        OldShapeLoader(URL[] urls) { super(urls, UltiLogin.class.getClassLoader()); }
        @Override public URL getResource(String name) {
            return name.startsWith(BASE.replace('.', '/')) ? findResource(name) : super.getResource(name);
        }
        @Override protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith(BASE + ".") || name.equals(VIEW.replace('/', '.')) || name.startsWith("compatprobe.")) {
                Class<?> type = findLoadedClass(name);
                if (type == null) { type = findClass(name); }
                if (resolve) { resolveClass(type); }
                loaded.add(name);
                return type;
            }
            return super.loadClass(name, resolve);
        }
    }
}
