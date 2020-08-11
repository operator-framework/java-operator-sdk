package com.github.containersolutions.operator.sample;

import java.util.List;

import io.fabric8.kubernetes.api.model.ServiceStatus;

public class ServiceSpec {


	private String testSuite;
	private ServiceService service;
	private List<ServiceDependency> dependencies;
	
	
	public String getTestSuite() {
		return testSuite;
	}
	public void setTestSuite(String testSuite) {
		this.testSuite = testSuite;
	}
	public ServiceService getService() {
		return service;
	}
	public void setService(ServiceService service) {
		this.service = service;
	}
	public List<ServiceDependency> getDependencies() {
		return dependencies;
	}
	public void setDependencies(List<ServiceDependency> dependencies) {
		this.dependencies = dependencies;
	}
}
