const API_HEALTH = "https://hr-api.cyond.com/actuator/health";

async function wakeApi() {
  await fetch(API_HEALTH, { method: "GET" });
}

export default {
  async scheduled(_controller: ScheduledController, _env: unknown, ctx: ExecutionContext) {
    ctx.waitUntil(wakeApi());
  },
  async fetch() {
    await wakeApi();
    return new Response("ok");
  },
};
