{{/*
Expand the name of the chart.
*/}}
{{- define "generic-operator.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "generic-operator.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "generic-operator.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "generic-operator.labels" -}}
helm.sh/chart: {{ include "generic-operator.chart" . }}
{{ include "generic-operator.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "generic-operator.selectorLabels" -}}
app.kubernetes.io/name: {{ include "generic-operator.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "generic-operator.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "generic-operator.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Image tag - defaults to chart appVersion
*/}}
{{- define "generic-operator.imageTag" -}}
{{- .Values.image.tag | default .Chart.AppVersion }}
{{- end }}

{{/*
Default verbs for primary resources
*/}}
{{- define "generic-operator.primaryVerbs" -}}
- get
- list
- watch
- patch
- update
{{- end }}

{{/*
Default verbs for primary resource status
*/}}
{{- define "generic-operator.primaryStatusVerbs" -}}
- get
- patch
- update
{{- end }}

{{/*
Default verbs for secondary resources
*/}}
{{- define "generic-operator.secondaryVerbs" -}}
- get
- list
- watch
- create
- update
- patch
- delete
{{- end }}
