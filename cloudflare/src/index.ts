import { Container, getContainer } from "@cloudflare/containers";

interface Env {
  API: DurableObjectNamespace<NexusHrContainer>;
  JWT_SECRET?: string;
  DB_URL?: string;
  DB_USER?: string;
  DB_PASSWORD?: string;
  AWS_REGION?: string;
  AWS_S3_BUCKET?: string;
  AWS_ENDPOINT_URL_S3?: string;
  AWS_ACCESS_KEY_ID?: string;
  AWS_SECRET_ACCESS_KEY?: string;
  RESEND_API_KEY?: string;
}

const SECRET_KEYS = [
  "JWT_SECRET",
  "DB_URL",
  "DB_USER",
  "DB_PASSWORD",
  "AWS_REGION",
  "AWS_S3_BUCKET",
  "AWS_ENDPOINT_URL_S3",
  "AWS_ACCESS_KEY_ID",
  "AWS_SECRET_ACCESS_KEY",
  "RESEND_API_KEY",
] as const;

function containerEnv(env: Env): Record<string, string> {
  const vars: Record<string, string> = {
    SPRING_PROFILES_ACTIVE: "prod",
    PORT: "8080",
    TZ: "Asia/Kolkata",
    JAVA_TOOL_OPTIONS:
      "-Xms64m -Xmx320m -XX:MaxMetaspaceSize=128m -Duser.timezone=Asia/Kolkata",
  };
  for (const key of SECRET_KEYS) {
    const value = env[key];
    if (value) {
      vars[key] = value;
    }
  }
  return vars;
}

export class NexusHrContainer extends Container<Env> {
  defaultPort = 8080;
  // Java attendance jobs run only while this process stays up.
  sleepAfter = "12h";

  constructor(ctx: ConstructorParameters<typeof Container<Env>>[0], env: Env) {
    super(ctx, env);
    this.envVars = containerEnv(env);
    this.enableInternet = true;
  }
}

async function wake(env: Env) {
  const container = getContainer(env.API, "nexushr");
  // Spring Boot takes longer than the 20 second default on a cold start.
  await container.startAndWaitForPorts({
    ports: 8080,
    startOptions: {
      envVars: containerEnv(env),
      enableInternet: true,
    },
    cancellationOptions: {
      portReadyTimeoutMS: 180_000,
      instanceGetTimeoutMS: 60_000,
    },
  });
  return container;
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    try {
      const container = await wake(env);
      return await container.fetch(request);
    } catch (error) {
      const message = error instanceof Error ? error.message : "API did not start";
      return new Response(JSON.stringify({ message }), {
        status: 503,
        headers: { "content-type": "application/json" },
      });
    }
  },

  async scheduled(_controller: ScheduledController, env: Env): Promise<void> {
    const container = await wake(env);
    await container.fetch("http://nexushr/actuator/health");
  },
};
