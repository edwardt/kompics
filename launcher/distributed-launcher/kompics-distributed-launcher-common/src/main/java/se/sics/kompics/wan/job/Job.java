package se.sics.kompics.wan.job;

import java.io.File;
import java.io.Serializable;
import java.util.List;

import se.sics.kompics.Request;
import se.sics.kompics.wan.config.Configuration;
import se.sics.kompics.wan.util.PomUtils;

public abstract class Job extends Request implements Serializable {

	private static final long serialVersionUID = 3831799496529156008L;

	protected final int id;

	protected final String repoId;
	protected final String repoUrl;

	protected final String groupId;
	protected final String artifactId;
	protected final String version;

	protected final String mainClass;
	protected final List<String> args;


	public Job(String groupId, String artifactId, String version, String mainClass, List<String> args,
			String repoId, String repoUrl) {
		this.groupId = groupId;
		this.artifactId = artifactId;
		this.version = version;
		this.mainClass = mainClass;
		this.args = args;
		this.repoId = repoId;
		this.repoUrl = repoUrl;
		this.id = PomUtils.generateJobId(groupId, artifactId, version);
	}
	
	public Job(String groupId, String artifactId, String version, String mainClass, List<String> args) {
		this(groupId, artifactId, version, mainClass, args, 
				Configuration.getDefaultRepoId(), 
				Configuration.getDefaultRepoUrl());
	}
	
	public Job(Job job)
	{
		this.id = job.getId();
		this.groupId = job.getGroupId();
		this.artifactId = job.getArtifactId();
		this.version = job.getVersion();
		this.mainClass = job.getMainClass();
		this.args = job.getArgs();
		this.repoId = job.getRepoId();
		this.repoUrl = job.getRepoUrl();
	}

	public int getId() {
		return id;
	}

	public String getGroupId() {
		return groupId;
	}

	public String getArtifactId() {
		return artifactId;
	}

	public String getVersion() {
		return version;
	}

	public String getRepoId() {
		return repoId;
	}

	public String getRepoUrl() {
		return repoUrl;
	}

	public String getMainClass() {
		return mainClass;
	}

	public List<String> getArgs() {
		return args;
	}

	public String[] getArgsAsArray() {
		return args.toArray(new String[args.size()]);
	}
	
	public File getPomFile()
	{
		return new File(getPomFilename());
	}
	
	public String getPomFilename()
	{
		String groupPath = PomUtils.groupIdToPath(groupId);
		String sepStr = PomUtils.sepStr();
    	String pomFileName = Configuration.KOMPICS_HOME + sepStr + groupPath + sepStr +
		artifactId + sepStr + version + sepStr + Configuration.POM_FILENAME;
    	return pomFileName;
	}

	public String getPomDirname()
	{
		String pomFilename = getPomFilename();
		int idx = pomFilename.lastIndexOf(Configuration.POM_FILENAME, 0);
		return pomFilename.substring(0, idx);
	}

	public String getDummyJarWithDependenciesName()
	{
		String sepStr = PomUtils.sepStr();
		String jarFileName = Configuration.KOMPICS_HOME + sepStr 
		+ PomUtils.groupIdToPath(groupId) + sepStr +
		getArtifactId() + sepStr + getVersion() + sepStr 
		+ "target" + sepStr + "dummy-0.1-jar-with-dependencies.jar";
		return jarFileName;
	}
	
	public File getJarFromRepoFile()
	{
		return new File(getJarFromRepoFilename());
	}
	
	public String getJarFromRepoFilename()
	{
		String sepStr = PomUtils.sepStr();
		String jarFileName = Configuration.MAVEN_REPO_LOCAL + sepStr 
		+ PomUtils.groupIdToPath(groupId) + sepStr +
		getArtifactId() + sepStr + getVersion() + sepStr 
		+ getArtifactId() + "-" + getVersion() + ".jar";
		
		return jarFileName;
	}
}
