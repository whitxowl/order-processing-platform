dev-up:
	docker compose --env-file .secrets/dev/.env up -d --build

dev-down:
	docker compose down -v

k3d-up:
	k3d cluster create platform --port "8080:30080@loadbalancer"
	kubectl apply -f infra/k8s/namespaces/namespaces.yaml
	kubectl create secret generic platform-secrets \
	  --from-env-file=.secrets/dev/.env -n dev --dry-run=client -o yaml | kubectl apply -f -
	helm upgrade --install platform infra/helm/charts/platform -n dev

inject-secrets:
	kubectl create secret generic platform-secrets \
	  --from-env-file=.secrets/dev/.env -n dev --dry-run=client -o yaml | kubectl apply -f -