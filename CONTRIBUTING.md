## Creating Releases
To release the project create a new github release. This will trigger the release workflow (implemented using GitHub Actions).
Set both tag and name ing a format: `v[major].[minor].[patch]`, sample: `v1.2.23`. 
Follow classic semver guidelines. Note that the version in the pom files   
is based on tag name not on release name in the release workflow.

