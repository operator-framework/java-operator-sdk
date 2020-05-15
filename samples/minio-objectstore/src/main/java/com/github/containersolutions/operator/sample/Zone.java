package com.github.containersolutions.operator.sample;

public class Zone {
    private String name;
    private Integer servers;
    private String mem;
    private String cpu;
   
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
   
    public Integer getServers() {
        return servers;
    }
    
    public void setServers(Integer servers) {
        this.servers = servers;
    }
    
    public String getMem() {
        return mem;
    }
    
    public void setMem(String mem) {
        this.mem = mem;
    }
    
    public String getCpu() {
        return cpu;
    }
    
    public void setCpu(String cpu) {
        this.cpu = cpu;
    }
}
