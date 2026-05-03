dev-up:
	docker compose --env-file .secrets/dev/.env up -d --build

dev-down:
	docker compose down -v

k3d-up:
	k3d cluster create platform --port "8080:30080@loadbalancer"
	kubectl apply -f infra/k8s/namespaces/namespaces.yaml
	helm repo add sealed-secrets https://bitnami-labs.github.io/sealed-secrets
	helm repo update
	helm upgrade --install sealed-secrets sealed-secrets/sealed-secrets \
		-n kube-system \
		--set fullnameOverride=sealed-secrets-controller \
		--wait
	kubectl apply -f infra/k8s/secrets/platform-sealed-secret.yaml
	helm upgrade --install platform infra/helm/charts/platform -n dev

inject-secrets:
	kubectl create secret generic platform-secrets \
	  --from-env-file=.secrets/dev/.env \
	  -n dev \
	  --dry-run=client -o yaml \
	  | kubeseal \
	      --controller-name=sealed-secrets-controller \
	      --controller-namespace=kube-system \
	      --format yaml \
	  > infra/k8s/secrets/platform-sealed-secret.yaml
	kubectl apply -f infra/k8s/secrets/platform-sealed-secret.yaml

backup-sealing-key:
	kubectl get secret -n kube-system \
	  -l sealedsecrets.bitnami.com/sealed-secrets-key \
	  -o yaml > sealed-secrets-master-key.yaml

restore-sealing-key:
	kubectl apply -f sealed-secrets-master-key.yaml
	kubectl rollout restart deployment sealed-secrets-controller -n kube-system