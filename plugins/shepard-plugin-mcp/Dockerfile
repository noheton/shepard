FROM python:3.12-slim

WORKDIR /app

COPY pyproject.toml .
COPY src/ src/

RUN pip install --no-cache-dir -e .

ENV SHEPARD_API_BASE=http://backend:8080
ENV PORT=8811

EXPOSE 8811

CMD ["shepard-mcp"]
