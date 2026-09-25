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
            for (String raw : module.dependencies()) {
                String targetId = raw;
                int delimiter = raw.indexOf("::");
                if (delimiter >= 0) {
                    targetId = raw.substring(0, delimiter);
                    String api = raw.substring(delimiter + 2);
                    if (api.isBlank()) {
                        error(module.element(), "Invalid dependency selector '" + raw + "'");
                    }
                }
                if (targetId.isBlank()) {
                    error(module.element(), "Invalid dependency selector '" + raw + "'");
                } else if (!modulesById.containsKey(targetId)) {
                    error(module.element(), "Module '" + module.id() + "' depends on missing module '" + targetId + "'");
                }
            }
        }
    }

    private void scanRootElements(RoundEnvironment roundEnv) {
        List<ModuleInfo> owners = modulesByPackage.values().stream()
                .sorted(Comparator.comparingInt((ModuleInfo info) -> info.packageName().length()).reversed())
                .toList();

        for (Element root : roundEnv.getRootElements()) {
            if (!(root instanceof TypeElement sourceType)) {
                continue;
            }

            PackageElement sourcePackageElement = processingEnv.getElementUtils().getPackageOf(sourceType);
            String sourcePackage = sourcePackageElement.getQualifiedName().toString();
            ModuleInfo sourceModule = ownerOf(sourcePackage, owners);
            if (sourceModule == null) {
                continue;
            }

            TreePath path = trees.getPath(sourceType);
            if (path == null) {
                continue;
            }
            CompilationUnitTree unit = path.getCompilationUnit();
            String unitKey = unit.getSourceFile() == null
                    ? sourceType.getQualifiedName().toString()
                    : unit.getSourceFile().toUri().toString();
            if (!scannedUnits.add(unitKey)) {
                continue;
            }

            new BoundaryScanner(sourceModule, owners).scan(unit, null);
        }
    }

    private ModuleInfo ownerOf(String packageName, List<ModuleInfo> modules) {
        for (ModuleInfo module : modules) {
            if (packageName.equals(module.packageName())
                    || packageName.startsWith(module.packageName() + ".")) {
                return module;
            }
        }
        return null;
    }

    private TypeElement enclosingType(Element element) {
        Element current = element;
        while (current != null && !(current instanceof TypeElement)) {
            current = current.getEnclosingElement();
        }
        return (TypeElement) current;
    }

    private void validateReference(
            ModuleInfo sourceModule,
            List<ModuleInfo> owners,
            TreePath path
    ) {
        Element referenced = trees.getElement(path);
        if (referenced == null) {
            return;
        }

        TypeElement targetType = enclosingType(referenced);
        if (targetType == null) {
            return;
        }

        String targetPackage = processingEnv.getElementUtils()
                .getPackageOf(targetType)
                .getQualifiedName()
                .toString();
        ModuleInfo targetModule = ownerOf(targetPackage, owners);
        if (targetModule == null || targetModule.id().equals(sourceModule.id())) {
            return;
        }

        String key = sourceModule.id() + "|" + targetType.getQualifiedName() + "|" + path.getLeaf();
        if (!reported.add(key)) {
            return;
        }

        if (targetPackage.equals(targetModule.packageName() + ".internal")
                || targetPackage.startsWith(targetModule.packageName() + ".internal.")) {
            errorAt(path, "Module '" + sourceModule.id() + "' cannot access internal type "
                    + targetType.getQualifiedName() + " from module '" + targetModule.id() + "'");
            return;
        }

        ModuleApi api = targetType.getAnnotation(ModuleApi.class);
        if (api == null) {
            errorAt(path, "Cross-module reference to " + targetType.getQualifiedName()
                    + " is not allowed because it is not annotated with @ModuleApi");
            return;
        }

        String apiName = api.value().trim();
        boolean allowed = sourceModule.dependencies().stream().anyMatch(raw -> {
            if (raw.equals(targetModule.id())) {
                return true;
            }
            return raw.equals(targetModule.id() + "::" + apiName);
        });
        if (!allowed) {
            errorAt(path, "Module '" + sourceModule.id() + "' must declare dependency '"
                    + targetModule.id() + "' or '" + targetModule.id() + "::" + apiName
                    + "' to access " + targetType.getQualifiedName());
        }
    }

    private void error(Element element, String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
    }

    private void errorAt(TreePath path, String message) {
        trees.printMessage(Diagnostic.Kind.ERROR, message, path.getLeaf(), path.getCompilationUnit());
    }
