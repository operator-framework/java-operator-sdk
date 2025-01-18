---
title: Using k8s' ETCD as your application DB
date: 2025-01-16
---

# FAQ: Is Kubernetes’ ETCD the Right Database for My Application?

## Answer

While the idea of moving your application data to Custom Resources (CRs) aligns with the "Cloud Native" philosophy, it often introduces more challenges than benefits. Let’s break it down:

---

### Top Reasons Why Storing Data in ETCD Through CRs Looks Appealing

1. **Storing application data as CRs enables treating your application’s data like infrastructure:**
   - **GitOps compatibility:** Declarative content can be stored in Git repositories, ensuring reproducibility.
   - **Infrastructure alignment:** Application data can follow the same workflow as other infrastructure components.

---

### Challenges of Using Kubernetes’ ETCD as Your Application’s Database

#### Technical Limitations:

- **Data Size Limitations 🔴:**
  - Each CR is capped at 1.5 MB by default. Raising this limit is possible but impacts cluster performance.
  - Kubernetes ETCD has a storage cap of 2 GB by default. Adjusting this limit affects the cluster globally, with potential performance degradation.

- **API Server Load Considerations 🟡:**
  - The Kubernetes API server is designed to handle infrastructure-level requests.
  - Storing application data in CRs might add significant load to the API server, requiring it to be scaled appropriately to handle both infrastructure and application demands.
  - This added load can impact cluster performance and increase operational complexity.

- **Guarantees 🟡:**
  - Efficient queries are hard to implement and there is no support for them.
  - ACID properties are challenging to leverage and everything holds mostly in read-only mode.

#### Operational Impact:

- **Lost Flexibility 🟡:**
  - Modifying application data requires complex YAML editing and full redeployment.
  - This contrasts with traditional databases that often feature user-friendly web UIs or APIs for real-time updates.

- **Infrastructure Complexity 🟠:**
  - Backup, restore, and lifecycle management for application data are typically separate from deployment workflows.
  - Storing both in ETCD mixes these concerns, complicating operations and standardization.

#### Security:

- **Governance and Security 🔴:**
  - Sensitive data stored in plain YAML may lack adequate encryption or access controls.
  - Applying governance policies over text-based files can become a significant challenge.

---

### When Might Using CRs Make Sense?

For small, safe subsets of data—such as application configurations—using CRs might be appropriate. However, this approach requires a detailed evaluation of the trade-offs.

---

### Conclusion

While it’s tempting to unify application data with infrastructure control via CRs, this introduces risks that can outweigh the benefits. For most applications, separating concerns by using a dedicated database is the more robust, scalable, and manageable solution.

---

### A Practical Example

A typical “user” described in JSON:

```json
{
  "username": "myname",
  "enabled": true,
  "email": "myname@test.com",
  "firstName": "MyFirstName",
  "lastName": "MyLastName",
  "credentials": [
    {
      "type": "password",
      "value": "test"
    },
    {
      "type": "token",
      "value": "oidc"
    }
  ],
  "realmRoles": [
    "user",
    "viewer",
    "admin"
  ],
  "clientRoles": {
    "account": [
      "view-profile",
      "change-group",
      "manage-account"
    ]
  }
}
```

This example represents about **0.5 KB of data**, meaning (with standard settings) a maximum of ~2000 users can be defined in the same CR.
Additionally:

- It contains **sensitive information**, which should be securely stored.
- Regulatory rules (like GDPR) apply.

---

### References

- [Using etcd as primary store database](https://stackoverflow.com/questions/41063238/using-etcd-as-primary-store-database)
