# Custom JupyterHub image with OAuthenticator + DockerSpawner installed.
# The base jupyterhub/jupyterhub:5 image ships only JupyterHub core; the OIDC
# authenticator and Docker single-user spawner are separate Python packages
# that the plugin's jupyterhub_config.py expects.
FROM jupyterhub/jupyterhub:5

RUN python3 -m pip install --no-cache-dir \
    oauthenticator \
    dockerspawner

# All other config (base_url, OIDC URLs, Docker single-user image, env vars)
# arrives at runtime via the bind-mounted /srv/jupyterhub/jupyterhub_config.py
