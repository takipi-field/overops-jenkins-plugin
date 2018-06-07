# OverOps Query Jenkins Plugin

The plugin provides a mechanism for querying OverOps as a post build step to ensure continuous reliability. Check OverOps for new errors and exceptions introduced by the build.   


## Installation
  Prequisites

  * Jenkins running on Java 1.8 or later
  


## Global Configuration

  Select Manage Jenkins -> Configure Plugin 
  scroll down to **OverOps Query Plugin**
  
  **OverOps URL:**  The complete url including port of the OverOps e.g. http://localhost:8080 or for SaaS  https://api.overops.com
  
  **OverOps Service ID*  OverOps Service ID (begins with S)
  
  **OverOps User**  OverOps username with access to the relevant events.
  
  **OverOps Password**  Password for OverOps user.
  
  **OverOps API Key**  When using an API key User and Password fields are ignored.
  
Test connection would show you a count of available metrics.  If the count shows 0 measurements, credentials are correct but    database may be wrong.  If credentials are incorrect you will receive an authentication error.
  

## Job Post Build Configuration
  **Application Name**  OverOps Application Name to match in Query
  
  **Deployment Name**   OverOps Deployment Name to match in Query.  Can make use of Jenkins Build Variables such as ${BUILD_NUMBER}.

  **Max Event Count**  Number of acceptable errors.  If query record count exceeds this limit and if Mark Build Unstable is selected, the build will be marked unstable. Set to -1 to ignore.

  **Max New Event Count**  Number of acceptable error introduced in this build.  If query record count exceeds this limit and if Mark Build Unstable is selected, the build will be marked unstable.  Set to -1 to ignore.
  
  **Retry Count**  Number of times to execute the query as a single post-build step.

  **Retry Interval**  Time to wait in between each query in seconds.

  **Mark Build Unstable**  Check if we should mark the build unstable if the Max Record Count is exceeded.  

  **Show Query Results**  Check if we should should display the query results in the Jenkins console.
