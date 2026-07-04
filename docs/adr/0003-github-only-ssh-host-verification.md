# GitHub-only SSH host verification

For v1, git sync supports GitHub SSH remotes only. Host-key verification is pinned to GitHub's published SSH fingerprints, which avoids insecure trust-on-first-use and avoids building a `known_hosts` management UX before the app has a safe custom-host enrollment flow. Supporting GitLab, Gitea, or self-hosted git over SSH requires explicit host-key enrollment/storage instead of accepting arbitrary hosts.
