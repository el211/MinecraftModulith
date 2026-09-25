package dev.oreo.modulith.processor;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import dev.oreo.modulith.core.ModuleApi;
import dev.oreo.modulith.core.PluginModule;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compile-time validator for module boundaries.
 *
 * <p>It rejects cross-module access to non-{@link ModuleApi} types, direct access to another
 * module's {@code internal} packages, undeclared module/API dependencies, duplicate module IDs,
 * and invalid public API declarations.</p>
 */
@SupportedAnnotationTypes({
        "dev.oreo.modulith.core.PluginModule",
        "dev.oreo.modulith.core.ModuleApi"
})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public final class ModuleArchitectureProcessor extends AbstractProcessor {
    private Trees trees;
    private final Map<String, ModuleInfo> modulesById = new LinkedHashMap<>();
    private final Map<String, ModuleInfo> modulesByPackage = new LinkedHashMap<>();
    private final Set<String> scannedUnits = new HashSet<>();
    private final Set<String> reported = new HashSet<>();
    private boolean metadataWritten;

    @Override
    public synchronized void init(javax.annotation.processing.ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        trees = Trees.instance(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        collectModules(roundEnv);
        validateApiDeclarations(roundEnv);

        if (!roundEnv.processingOver()) {
            scanRootElements(roundEnv);
        } else if (!metadataWritten) {
            validateDeclaredDependencies();
            writeMetadata();
            metadataWritten = true;
        }
        return false;
    }

    private void collectModules(RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(PluginModule.class)) {
            if (!(element instanceof TypeElement type)) {
                continue;
            }

            PluginModule annotation = type.getAnnotation(PluginModule.class);
            String id = annotation.value().trim();
            String packageName = processingEnv.getElementUtils().getPackageOf(type).getQualifiedName().toString();

            if (id.isEmpty()) {
                error(type, "MinecraftModulith module id cannot be blank");
                continue;
            }

            ModuleInfo info = new ModuleInfo(
                    id,
                    packageName,
                    type.getQualifiedName().toString(),
                    List.of(annotation.dependencies()),
                    annotation.configuration(),
                    type
            );

            ModuleInfo previous = modulesById.putIfAbsent(id, info);
            if (previous != null && !previous.className().equals(info.className())) {
                error(type, "Duplicate MinecraftModulith module id '" + id + "' also used by "
                        + previous.className());
            } else {
                modulesByPackage.put(packageName, info);
            }
        }
    }

    private void validateApiDeclarations(RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(ModuleApi.class)) {
            if (!(element instanceof TypeElement type)) {
                continue;
            }

            ModuleApi api = type.getAnnotation(ModuleApi.class);
            String apiName = api.value().trim();
            if (apiName.isEmpty() || apiName.contains("::")) {
                error(type, "@ModuleApi name must be non-blank and cannot contain '::'");
            }
            if (!type.getModifiers().contains(Modifier.PUBLIC)) {
                error(type, "@ModuleApi type must be public: " + type.getQualifiedName());
            }

            String packageName = processingEnv.getElementUtils().getPackageOf(type).getQualifiedName().toString();
            if (packageName.contains(".internal") || packageName.endsWith(".internal")) {
                error(type, "@ModuleApi cannot be declared from an internal package: " + packageName);
            }
        }
    }

    private void validateDeclaredDependencies() {
        for (ModuleInfo module : modulesById.values()) {
