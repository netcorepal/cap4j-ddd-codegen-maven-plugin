package org.netcorepal.cap4j.ddd.codegen;

import org.netcorepal.cap4j.ddd.codegen.misc.SourceFileUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;

import java.io.*;
import java.util.*;

import static org.netcorepal.cap4j.ddd.codegen.misc.SourceFileUtils.writeLine;

/**
 * @author binking338
 * @date 2022-02-14
 */
@Mojo(name = "gen-repository")
public class GenRepositoryMojo extends AbstractMojo {
    /**
     * 是否多项目
     *
     * @parameter expression="${multiModule}"
     */
    @Parameter(property = "multiModule", defaultValue = "false")
    private Boolean multiModule = false;
    /**
     * domain模块名称
     *
     * @parameter expression="${moduleNameDomain}"
     */
    @Parameter(property = "moduleNameDomain", defaultValue = "-domain")
    private String moduleNameDomain = "-domain";
    /**
     * application模块名称
     *
     * @parameter expression="${moduleNameApplication}"
     */
    @Parameter(property = "moduleNameApplication", defaultValue = "-application")
    private String moduleNameApplication = "-application";
    /**
     * adapter模块名称
     *
     * @parameter expression="${moduleNameAdapter}"
     */
    @Parameter(property = "moduleNameAdapter", defaultValue = "-adapter")
    private String moduleNameAdapter = "-adapter";

    /**
     * 基础包路径
     *
     * @parameter expression="${basePackage}"
     */
    @Parameter(property = "basePackage", defaultValue = "")
    private String basePackage = "";

    /**
     * 忽略聚合根，逗号分割
     *
     * @parameter expression="${ignoreAggregateRoots}"
     */
    @Parameter(property = "ignoreAggregateRoots", defaultValue = "")
    private String ignoreAggregateRoots = "";


    /**
     * 聚合仓储基类型
     *
     * @parameter expression="${aggregateRepositoryBaseClass}"
     */
    @Parameter(property = "aggregateRepositoryBaseClass", defaultValue = "")
    private String aggregateRepositoryBaseClass = "";

    /**
     * 聚合唯一标识类型
     *
     * @parameter expression="${aggregateIdentityClass}"
     */
    @Parameter(property = "aggregateIdentityClass", defaultValue = "Long")
    private String aggregateIdentityClass = "Long";

    /**
     * 聚合仓储自定义代码
     *
     * @parameter expression="${aggregateRepositoryCustomerCode}"
     */
    @Parameter(property = "aggregateRepositoryCustomerCode", defaultValue = "")
    private String aggregateRepositoryCustomerCode = "";

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        this.getLog().info("开始生成仓储代码");

        File currentDirFile = new File("");
        String absoluteCurrentDir = currentDirFile.getAbsolutePath();

        String domainModulePath, adapterModulePath;
        if (multiModule) {
            getLog().info("多模块项目");
            String projectDir = new File(absoluteCurrentDir).getParent();
            getLog().info("项目目录：" + projectDir);

            domainModulePath = Arrays.stream(new File(projectDir).listFiles())
                    .filter(path -> path.getAbsolutePath().endsWith(moduleNameDomain))
                    .findFirst().get().getAbsolutePath();
            adapterModulePath = Arrays.stream(new File(projectDir).listFiles())
                    .filter(path -> path.getAbsolutePath().endsWith(moduleNameAdapter))
                    .findFirst().get().getAbsolutePath();
        } else {
            getLog().info("单模块项目");
            domainModulePath = absoluteCurrentDir;
            adapterModulePath = absoluteCurrentDir;
        }
        getLog().info("模块目录：" + absoluteCurrentDir);
        String basePackage = org.apache.commons.lang3.StringUtils.isNotBlank(this.basePackage)
                ? this.basePackage
                : SourceFileUtils.resolveBasePackage(domainModulePath);
        getLog().info("基础包名：" + basePackage);
        if (StringUtils.isBlank(aggregateRepositoryBaseClass)) {
            // 默认聚合仓储基类
            aggregateRepositoryBaseClass = "org.netcorepal.cap4j.ddd.domain.repo.AggregateRepository<${EntityType}, ${IdentityType}>";
        }
        if(StringUtils.isBlank(aggregateRepositoryCustomerCode)){
            aggregateRepositoryCustomerCode =
                    "@org.springframework.stereotype.Component\n" +
                    "    public static class ${EntityType}JpaRepositoryAdapter extends org.netcorepal.cap4j.ddd.domain.repo.AbstractJpaRepository<${EntityType}, ${IdentityType}>\n" +
                    "    {\n" +
                    "        public ${EntityType}JpaRepositoryAdapter(org.springframework.data.jpa.repository.JpaSpecificationExecutor<${EntityType}> jpaSpecificationExecutor, org.springframework.data.jpa.repository.JpaRepository<${EntityType}, ${IdentityType}> jpaRepository) {\n" +
                    "            super(jpaSpecificationExecutor, jpaRepository);\n" +
                    "        }\n" +
                    "    }" +
                    "";
        }
        try {
            //renameAggregateRepositorySourceFiles(basePackage, adapterModulePath);
            List<File> files = SourceFileUtils.loadFiles(domainModulePath);
            files.forEach(file -> {
                String content = "";
                try {
                    content = FileUtils.fileRead(file);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                boolean isAggregateRoot = Arrays.stream(content.replace("\r\n", "\n").split("\n"))
                        .anyMatch(line -> line.matches("^\\s*(@(\\s*[A-Za-z_][A-Za-z0-9_]*\\s*\\.?)+)+$") && line.matches("^\\s*@([^.]*\\.)*AggregateRoot\\s*\\s*$")
                                        || line.matches("^\\s*\\/\\*\\s*@AggregateRoot\\s*\\*\\/\\s*$"));
                if (isAggregateRoot) {
                    this.getLog().info("发现聚合根类型: " + SourceFileUtils.resolveClassName(file.getAbsolutePath()));

                    String simpleClassName = SourceFileUtils.resolveSimpleClassName(file.getAbsolutePath());
                    if (Arrays.stream(ignoreAggregateRoots.split("\\,"))
                            .anyMatch(i -> i.equalsIgnoreCase(simpleClassName))) {
                        return;
                    }
                    try {
                        writeAggregateRepositorySourceFile(file.getAbsolutePath(), basePackage, adapterModulePath);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (Exception e) {
            this.getLog().error("发生异常，", e);
        }

        this.getLog().info("结束生成仓储代码");
    }

    /**
     * 返回能否从新生成实体
     *
     * @param filePath
     * @param customerLines
     * @return
     * @throws IOException
     */
    public boolean readCustomerSourceFile(String filePath, List<String> importLines, List<String> annotationLines, List<String> customerLines) throws IOException {
        if (FileUtils.fileExists(filePath + ".remove")) {
            FileUtils.rename(new File(filePath + ".remove"), new File(filePath));
        }
        if (FileUtils.fileExists(filePath)) {
            String content = FileUtils.fileRead(filePath);
            List<String> lines = Arrays.asList(content.replace("\r\n", "\n").split("\n"));
            int startLine = 0;
            int endLine = 0;
            int startClassLine = 0;
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
//                if (line.contains("【自定义代码开始】")) {
//                    startLine = i;
//                } else if (line.contains("【自定义代码结束】")) {
//                    endLine = i;
//                } else
                if (line.trim().startsWith("public") && startClassLine == 0) {
                    startClassLine = i;
                    startLine = i;
                } else if ((line.trim().startsWith("@") || annotationLines.size() > 0) && startClassLine == 0) {
                    annotationLines.add(line);
                    getLog().debug("a " + line);
                } else if ((annotationLines.size() == 0 && startClassLine == 0)) {
                    importLines.add(line);
                    getLog().debug("i " + line);
                } else if (startLine > 0 && endLine == 0) {
                    customerLines.add(line);
                }
            }

            for (int i = customerLines.size() - 1; i >= 0; i--) {
                String line = customerLines.get(i);
                if (line.contains("}")) {
                    customerLines.remove(i);
                    if (!line.equalsIgnoreCase("}")) {
                        customerLines.add(i, line.substring(0, line.lastIndexOf("}")));
                    }
                    break;
                }
                customerLines.remove(i);
            }
            customerLines.forEach(l -> getLog().debug("c " + l));
            if (startLine + 1 >= endLine) {
                return false;
            }
            FileUtils.removePath(filePath);
        }
        return true;
    }

    public void renameAggregateRepositorySourceFiles(String basePackage, String baseDir) {
        String packageName = basePackage + ".adapter.domain.repositories";

        List<File> files = SourceFileUtils.loadFiles(SourceFileUtils.resolveDirectory(baseDir, packageName));
        for (File file : files) {
            if (file.getAbsolutePath().endsWith(".java")) {
                try {
                    FileUtils.rename(file, new File(file.getAbsolutePath() + ".remove"));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void writeAggregateRepositorySourceFile(String entitySourceFilePath, String basePackage, String baseDir) throws IOException {
        String packageName = basePackage + ".adapter.domain.repositories";

        String simpleClassName = SourceFileUtils.resolveSimpleClassName(entitySourceFilePath);

        new File(SourceFileUtils.resolveDirectory(baseDir, packageName)).mkdirs();
        String filePath = SourceFileUtils.resolveSourceFile(baseDir, packageName, simpleClassName + "Repository");
        Optional<File> fileAlreadyExisit = SourceFileUtils.findJavaFileBySimpleClassName(baseDir, simpleClassName + "Repository");
        if (fileAlreadyExisit.isPresent()) {
            filePath = fileAlreadyExisit.get().getAbsolutePath();
            packageName = SourceFileUtils.resolvePackage(filePath);
            FileUtils.rename(fileAlreadyExisit.get(), new File(fileAlreadyExisit.get().getAbsolutePath() + ".remove"));
        }

        List<String> customerLines = new ArrayList<>();
        List<String> importLines = new ArrayList<>();
        List<String> annotationLines = new ArrayList<>();
        readCustomerSourceFile(filePath, importLines, annotationLines, customerLines);
//        importLines.removeIf(l -> l.contains(".convention.AggregateRepository;"));
        importLines.removeIf(l -> l.contains("." + simpleClassName + ";"));

        String className = SourceFileUtils.resolveClassName(entitySourceFilePath);
        BufferedWriter out = new BufferedWriter(new FileWriter(filePath));
        writeLine(out, "package " + packageName + ";");
        writeLine(out, "");
//        writeLine(out, "import " + basePackage + ".convention.AggregateRepository;");
        writeLine(out, "import " + className + ";");
        if (importLines.size() > 0) {
            if (StringUtils.isEmpty(importLines.get(0))) {
                importLines.remove(0);
            }
            importLines.forEach(l -> writeLine(out, l));
        } else {
            writeLine(out, "");
            writeLine(out, "/**");
            writeLine(out, " * 本文件由[gen-ddd-maven-plugin]生成");
            writeLine(out, " */");
        }
        writeLine(out, "public interface " + simpleClassName + "Repository extends " + aggregateRepositoryBaseClass.replace("${EntityType}", simpleClassName).replace("${IdentityType}", aggregateIdentityClass) + " {");
        if (customerLines.size() > 0) {
            for (String line : customerLines) {
                writeLine(out, line);
            }
        } else {
            writeLine(out, "    // 【自定义代码开始】本段落之外代码由[gen-ddd-maven-plugin]维护，请不要手工改动");
            writeLine(out, "");
            writeLine(out, "    " + aggregateRepositoryCustomerCode.replace("${EntityType}", simpleClassName).replace("${IdentityType}", aggregateIdentityClass));
            writeLine(out, "");
            writeLine(out, "    // 【自定义代码结束】本段落之外代码由[gen-ddd-maven-plugin]维护，请不要手工改动");

        }
        writeLine(out, "}");
        out.close();
    }
}
