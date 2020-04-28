## Creating Releases
To release the project create a new github release. This will trigger the release workflow (implemented using GitHub Actions).
Set both tag and name ing a format: `v[major].[minor].[patch]`, sample: `v1.2.23`. 
Follow classic semver guidlinces. Note that the version is in the pom files is 
calculated based on tag not by release name in the release workflow.

