workflow "Maven build" {
  resolves = ["GitHub Action for Maven"]
  on = "push"
}

action "GitHub Action for Maven" {
  uses = "LucaFeger/action-maven-cli@765e218a50f02a12a7596dc9e7321fc385888a27"
  runs = "mvn"
  args = "package"
}
