package org.netcorepal.cap4j.ddd.codegen;

import com.alibaba.fastjson.JSON;
import lombok.Data;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.netcorepal.cap4j.ddd.codegen.misc.SourceFileUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * 生成项目目录结构
 *
 * @author binking338
 * @date 2024/8/15
 */
@Mojo(name = "gen-arch")
public class GenArchMojo extends AbstractMojo {

    /**
     * 基础包路径
     *
     * @parameter expression="${basePackage}"
     */
    @Parameter(property = "basePackage", defaultValue = "")
    private String basePackage = "";

    /**
     * 模板文件地址
     *
     * @parameter expression="${archTemplate}"
     */
    @Parameter(property = "archTemplate", defaultValue = "")
    private String archTemplate = "";

    /**
     * 脚手架模板配置节点
     */
    @Data
    public static class PathNode {
        /**
         * 节点类型：root|dir|file
         */
        String type;
        /**
         * 节点名称
         */
        String name;
        /**
         * 模板源类型：raw|url
         */
        String format = "raw";
        /**
         * 模板数据数据
         */
        String data;
        /**
         * 冲突处理：skip|warn|overwrite
         */
        String conflict = "warn";

        /**
         * 下级节点
         */
        List<PathNode> children;
    }

    private String projectGroupId = "";
    private String projectArtifactId = "";
    private String projectVersion = "";

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        String templateContent = "";
        try {
            if (null == archTemplate || archTemplate.isEmpty()) {
//                templateContent = SourceFileUtils.loadResourceFileContent("template.json");
                getLog().error("请设置archTemplate参数");
                return;
            } else {
                templateContent = SourceFileUtils.loadFileContent(archTemplate);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if(basePackage==null || basePackage.isEmpty()){
            getLog().warn("请设置basePackage参数");
            return;
        }
        MavenProject mavenProject = ((MavenProject) getPluginContext().get("project"));
        if (mavenProject != null) {
            projectGroupId = mavenProject.getGroupId();
            projectArtifactId = mavenProject.getArtifactId();
            projectVersion = mavenProject.getVersion();
        }
        PathNode arch = JSON.parseObject(templateContent, PathNode.class);

        // 项目结构解析
        String projectDir;
        projectDir = new File("").getAbsolutePath();
        getLog().info("项目目录：" + projectDir);

        render(arch, projectDir);
    }

    public void render(PathNode pathNode, String parentPath) {
        String path = parentPath + File.separator + pathNode.name;
        getLog().info("创建 " + escapePath(path));
        switch (pathNode.type) {
            case "file":
                renderFile(pathNode, parentPath);
                break;
            case "dir":
                renderDir(pathNode, parentPath);
                if (pathNode.children != null) {
                    for (PathNode childPathNode : pathNode.children) {
                        render(childPathNode, path);
                    }
                }
                break;
            case "root":
                if (pathNode.children != null) {
                    for (PathNode childPathNode : pathNode.children) {
                        render(childPathNode, parentPath);
                    }
                }
                break;
        }
    }

    public void renderDir(PathNode pathNode, String parentPath) {
        if (!"dir".equalsIgnoreCase(pathNode.type)) {
            throw new RuntimeException("节点类型必须是文件");
        }
        if (pathNode.name == null || pathNode.name.isEmpty()) {
            throw new RuntimeException("模板节点配置 name 不得为空 parentPath = " + parentPath);
        }
        String path = parentPath + File.separator + pathNode.name;
        path = escapePath(path);

        new File(path).mkdirs();
    }

    public void renderFile(PathNode pathNode, String parentPath) {
        if (!"file".equalsIgnoreCase(pathNode.type)) {
            throw new RuntimeException("节点类型必须是文件");
        }
        if (pathNode.name == null || pathNode.name.isEmpty()) {
            throw new RuntimeException("模板节点配置 name 不得为空 parentPath = " + parentPath);
        }
        String path = parentPath + File.separator + pathNode.name;
        path = escapePath(path);

        String content = "";
        switch (pathNode.format) {
            case "raw":
                content = pathNode.data;
                break;
            case "url":
                try {
                    content = SourceFileUtils.loadFileContent(pathNode.data);
                } catch (IOException ex) {
                    getLog().error("获取模板源文件异常", ex);
                }
                break;
        }
        content = escapeContent(content);
        if (FileUtils.fileExists(path)) {
            switch (pathNode.conflict) {
                case "warn":
                    getLog().warn("文件已存在：" + path);
                    return;
                case "overwrite":
                    getLog().info("文件将覆盖：" + path);
                    break;
                case "skip":
                default:
                    getLog().info("文件已存在：" + path);
                    return;
            }
        }

        try {
            FileUtils.fileDelete(path);
            FileUtils.fileWrite(path, "utf-8", content);
        } catch (IOException e) {
            getLog().error("写入模板文件异常", e);
        }
    }

    public String escapePath(String path) {
        path = path.replace("${basePackage}", basePackage.replace(".", File.separator));
        if (projectArtifactId != null && !projectArtifactId.isEmpty()) {
            path = path.replace("${artifactId}", projectArtifactId);
        }
        return path;
    }

    public String escapeContent(String content) {
        content = content.replace("${basePackage}", basePackage);
        content = content.replace("${groupId}", projectGroupId);
        content = content.replace("${artifactId}", projectArtifactId);
        content = content.replace("${version}", projectVersion);
        content = content.replace("${symbol_pound}", "#");
        content = content.replace("${symbol_escape}", "\\");
        content = content.replace("${symbol_dollar}", "$");
        return content;
    }
}
