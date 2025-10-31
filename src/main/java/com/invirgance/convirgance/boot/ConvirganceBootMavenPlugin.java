/*
 * The MIT License
 *
 * Copyright 2025 INVIRGANCE LLC.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.invirgance.convirgance.boot;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.jetty.ee10.quickstart.PreconfigureQuickStartWar;
import org.eclipse.jetty.util.resource.PathResourceFactory;
import org.eclipse.jetty.util.resource.Resource;

/**
 *
 * @author jbanes
 */
@Mojo(name="boot", defaultPhase = LifecyclePhase.PACKAGE)
public class ConvirganceBootMavenPlugin extends AbstractMojo
{
    private String convirganceBoot = "com.invirgance:convirgance-boot:0.2.0";
    private String mainClass = "com.invirgance.convirgance.boot.ConvirganceBoot";
    private List<String> files = new ArrayList<>();
    private List<String> libraries = new ArrayList<>();
    
    @Parameter(defaultValue="${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repositorySystemSession;
    
    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
    private List<RemoteRepository> remoteProjectRepositories;

    private void packageFile(ZipOutputStream out, File source, String name) throws MojoExecutionException
    {
        byte[] data = new byte[4096];
        int count;
        
        
        try(var in = new FileInputStream(source))
        {
            out.putNextEntry(new ZipEntry(name));

            while((count = in.read(data)) > 0) out.write(data, 0, count);

            out.closeEntry();
        }
        catch(IOException e)
        {
            throw new MojoExecutionException(e);
        }
    }
    
    private void writeLibraries(JarOutputStream out) throws MojoExecutionException
    {
        JarEntry entry = new JarEntry("libraries");
        
        try
        {
            out.putNextEntry(entry);

            for(var library : libraries)
            {
                out.write(("/" + library + "\n").getBytes("UTF-8"));
            }
            
            out.closeEntry();
        }
        catch(IOException e)
        {
            throw new MojoExecutionException(e);
        }
    }
    
    private void packageLibrary(JarOutputStream out, String artifact) throws MojoExecutionException
    {
        File jar = getArtifact(artifact);
        JarEntry entry;
        
        byte[] data = new byte[4096];
        int count;
        
        try(var in = new FileInputStream(jar))
        {
            entry = new JarEntry("lib/" + jar.getName());
            
            libraries.add(entry.getName());
            out.putNextEntry(entry);
            
            while((count = in.read(data)) > 0) out.write(data, 0, count);
            
            out.closeEntry();
        }
        catch(IOException e)
        {
            throw new MojoExecutionException(e);
        }
    }
    
    private void assembleJar(JarOutputStream out) throws MojoExecutionException
    {
        byte[] data = new byte[4096];
        int count;
        
        JarEntry entry;
        JarEntry write;
        
        MavenProject project = getPOM(convirganceBoot);
        File jar = getArtifact(convirganceBoot);
        
        try(var in = new JarInputStream(new FileInputStream(jar)))
        {
            while((entry = in.getNextJarEntry()) != null)
            {
                if(entry.isDirectory()) continue;
                if(entry.getName().endsWith("Startup.class")) continue;
                if(entry.getName().startsWith("META-INF")) continue;
                
                write = new JarEntry(entry.getName());

                this.files.add(entry.getName().toLowerCase());
                out.putNextEntry(write);
                
                while((count = in.read(data)) > 0) out.write(data, 0, count);
                
                in.closeEntry();
            }
        }
        catch(IOException e)
        {
            throw new MojoExecutionException(e);
        }
    }
    
    private void packageApplication(File war, File jar, List<String> artifacts) throws MojoExecutionException
    {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.put(Attributes.Name.MAIN_CLASS, mainClass);

        try(var out = new JarOutputStream(new FileOutputStream(jar), manifest))
        {
            assembleJar(out);
            packageFile(out, war, "root.war");
            packageFile(out, new File(war.toString().replace(".war", "/WEB-INF/quickstart-web.xml")), "quickstart-web.xml");
            packageLibrary(out, convirganceBoot);
            
            for(var artifact : artifacts)
            {
                packageLibrary(out, artifact);
            }
            
            writeLibraries(out);
        }
        catch(IOException e)
        {
            throw new MojoExecutionException(e);
        }
    }
    
    private boolean compareVersions(String coordinates, String next)
    {
        String version = coordinates.split(":")[2];
        String[] semvar1 = version.split("\\.");
        String[] semvar2 = next.split("\\.");
        int length = Math.min(semvar1.length, semvar2.length);
        
        Integer number1;
        Integer number2;
        
        for(int i=0; i<length; i++)
        {
            try { number1 = Integer.valueOf(semvar1[i]); } catch(NumberFormatException e) { number1 = null; }
            try { number2 = Integer.valueOf(semvar2[i]); } catch(NumberFormatException e) { number2 = null; }
            
            if(number1 == null || number2 == null)
            {
                if(semvar1[i].compareTo(semvar2[i]) > 0) return false;
                if(semvar1[i].compareTo(semvar2[i]) < 0) return true;
            }
            else
            {
                if(number1.compareTo(number2) > 0) return false;
                if(number1.compareTo(number2) < 0) return true;
            }
        }
        
        if(semvar1.length > semvar2.length) return false;
        
        return true;
    }
    
    private boolean updateList(List<String> artifacts, String groupId, String artifactId, String version)
    {
        String partial = groupId + ":" + artifactId;
        
        for(int i=0; i<artifacts.size(); i++)
        {
            if(artifacts.get(i).startsWith(partial + ":"))
            {
                if(compareVersions(artifacts.get(i), version))
                {
                    artifacts.remove(i);
                    artifacts.add(partial+":"+version);
                }
                
                return false;
            }
        }
        
        artifacts.add(partial+":"+version);
        
        return true;
    }
    
    private void mergeLists(List<String> artifacts, List<String> children)
    {
        String[] components;
        
        for(var child : children)
        {
            components = child.split(":");
            
            updateList(artifacts, components[0], components[1], components[2]);
        }
    }
    
    private List<String> getArtifacts(String coordinate) throws MojoExecutionException
    {
        var project = getPOM(coordinate);
        var artifacts = new ArrayList<String>();
        
        String version;
        List<Exclusion> exclusions;

        for(var dependency : project.getDependencies())
        {
            version = dependency.getVersion();
            exclusions = dependency.getExclusions(); // TODO: Check exclusions

            if("true".equals(dependency.getOptional())) continue;
            if("provided".equals(dependency.getScope())) continue;
            if("test".equals(dependency.getScope())) continue;
            if("${project.version}".equals(version)) version = project.getVersion();
            if(version != null && version.startsWith("${") && version.endsWith("}")) version = project.getProperties().getProperty(version.substring(2, version.length()-1));
            if(version == null) version = getLatestVersion(dependency.getGroupId()+":"+dependency.getArtifactId()+":"+version);

            if(updateList(artifacts, dependency.getGroupId(), dependency.getArtifactId(), version))
            {
                mergeLists(artifacts, getArtifacts(dependency.getGroupId()+":"+dependency.getArtifactId()+":"+version));
            }
        }
        
        return artifacts;
    }
    
    private File getArtifact(String coordinates)
    {
        var item = new DefaultArtifact(coordinates);
        var manager = repositorySystemSession.getLocalRepositoryManager();
        var repo = manager.getRepository();
        
        return new File(repo.getBasedir() + "/" + manager.getPathForLocalArtifact(item));
    }
    
    private File getPOMFile(String coordinates)
    {
        var components = coordinates.split(":");
        var item = new DefaultArtifact(components[0]+":"+components[1]+":pom:"+components[2]);
        var manager = repositorySystemSession.getLocalRepositoryManager();
        var repo = manager.getRepository();
        
        return new File(repo.getBasedir() + "/" + manager.getPathForLocalArtifact(item));
    }
    
    private MavenProject getPOM(String coordinates) throws MojoExecutionException
    {
        var reader = new MavenXpp3Reader();
        var jar = getPOMFile(coordinates);
        
        Model model = null;
        
        try(var in = new FileInputStream(jar))
        {
            model = reader.read(in);
        }
        catch(Exception e)
        {
            throw new MojoExecutionException(e);
        }
        
        if(model == null) throw new MojoExecutionException("Could not find POM for dependency " + coordinates);
        
        return new MavenProject(model);
    }
    
    private void configureQuickstart(String directory) throws MojoExecutionException
    {
        Resource resource = new PathResourceFactory().newResource(Paths.get(directory));
        
        try
        {
            PreconfigureQuickStartWar.preconfigure(null, resource, null);
        }
        catch(Exception e)
        {
            throw new MojoExecutionException(e);
        }
    }

    public String getLatestVersion(String coordinates)
    {
        var components = coordinates.split(":");
        var jar = getArtifact(coordinates);
        
        String version = null;
        String partial = components[0]+":"+components[1];

        while(!jar.exists()) jar = jar.getParentFile();
 
        for(var file : jar.listFiles())
        {
            if(!file.isDirectory()) continue;
            if(!getArtifact(partial+":"+file.getName()).exists()) continue;
            
            if(version == null || compareVersions(partial+":"+version, file.getName()))
            {
                version = file.getName();
            }
        }
        
        return version;
    }
    
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        var context = super.getPluginContext();
        var project = (MavenProject)context.get("project");
        var build = project.getBuild();
        
        var war = build.getDirectory() + "/" + build.getFinalName() + "." + project.getPackaging();
        var jar = build.getDirectory() + "/" + build.getFinalName() + ".jar";
        var lock = new File(".virge/lock");
        
        var artifacts = getArtifacts(convirganceBoot);
        
        if(!project.getPackaging().equalsIgnoreCase("war")) throw new MojoFailureException("Only WAR projects are supported");
        if(lock.exists()) lock.delete();
        
        getLog().info("Repackaging " + war + " into executaable " + jar);

        configureQuickstart(build.getDirectory() + "/" + build.getFinalName());
        packageApplication(new File(war), new File(jar), artifacts);
    }
    
}