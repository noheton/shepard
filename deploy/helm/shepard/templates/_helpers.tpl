{{/*
Helper templates for the shepard umbrella chart.
Phase 1 SKELETON — minimal helpers sufficient for stubs.
*/}}

{{/*
Expand the name of the chart.
*/}}
{{- define "shepard.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this.
*/}}
{{- define "shepard.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{/*
Chart name + version label.
*/}}
{{- define "shepard.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Common labels.
*/}}
{{- define "shepard.labels" -}}
helm.sh/chart: {{ include "shepard.chart" . }}
{{ include "shepard.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{/*
Selector labels (must remain stable for matchLabels).
*/}}
{{- define "shepard.selectorLabels" -}}
app.kubernetes.io/name: {{ include "shepard.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{/*
Per-component fullname (e.g. shepard-backend, shepard-frontend).
*/}}
{{- define "shepard.componentFullname" -}}
{{- printf "%s-%s" (include "shepard.fullname" .root) .component | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
ServiceAccount name.
*/}}
{{- define "shepard.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
{{ default (include "shepard.fullname" .) .Values.serviceAccount.name }}
{{- else -}}
{{ default "default" .Values.serviceAccount.name }}
{{- end -}}
{{- end -}}

{{/*
Backend image (combines repository + tag fallback to AppVersion).
*/}}
{{- define "shepard.backendImage" -}}
{{- $registry := .Values.image.registry -}}
{{- $repo := .Values.image.backend.repository -}}
{{- $tag := default .Chart.AppVersion .Values.image.backend.tag -}}
{{- printf "%s/%s:%s" $registry $repo $tag -}}
{{- end -}}

{{/*
Frontend image.
*/}}
{{- define "shepard.frontendImage" -}}
{{- $registry := .Values.image.registry -}}
{{- $repo := .Values.image.frontend.repository -}}
{{- $tag := default .Chart.AppVersion .Values.image.frontend.tag -}}
{{- printf "%s/%s:%s" $registry $repo $tag -}}
{{- end -}}
