/**
 * V2UI-CHANNEL-ANNO-CLIENT — unit tests for {@link AnnotatedChannel}, the typed-client
 * wrapper over `TimeseriesChannelAnnotationsApi` (listChannelAnnotations /
 * createChannelAnnotation / deleteChannelAnnotation).
 *
 * Covers: list happy-path + 404-swallow, delete via appId lookup, create
 * forwarding, and missing-appId guard on delete-without-fetch.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ref } from "vue";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";

vi.mock("~/composables/common/api/useV2ShepardApi", () => ({
  useV2ShepardApi: vi.fn(),
}));

const mockList = vi.fn();
const mockCreate = vi.fn();
const mockDelete = vi.fn();

beforeEach(() => {
  vi.clearAllMocks();
  (useV2ShepardApi as ReturnType<typeof vi.fn>).mockReturnValue(
    ref({
      listChannelAnnotations: mockList,
      createChannelAnnotation: mockCreate,
      deleteChannelAnnotation: mockDelete,
    }),
  );
});

const CONTAINER = "01928eaa-0000-7000-8000-000000000042";
const CHANNEL = "01928eaa-1234-7000-9000-aaaaaaaaaaaa";
const ANNO_APP_ID = "0192anno-0000-7000-8000-000000000001";
const ANNO: { id: number; appId: string; propertyIRI: string; valueIRI: string } = {
  id: 5,
  appId: ANNO_APP_ID,
  propertyIRI: "http://example.org/prop",
  valueIRI: "http://example.org/val",
};

describe("AnnotatedChannel — typed TimeseriesChannelAnnotationsApi wrapper", () => {
  it("fetchAnnotations delegates to listChannelAnnotations with containerAppId + channelShepardId", async () => {
    mockList.mockResolvedValue([ANNO]);
    const { AnnotatedChannel } = await import("~/composables/annotated");

    const out = await new AnnotatedChannel(CONTAINER, CHANNEL).fetchAnnotations();

    expect(mockList).toHaveBeenCalledWith({
      appId: CONTAINER,
      channelShepardId: CHANNEL,
    });
    expect(out).toHaveLength(1);
    expect(out[0]).toMatchObject({ id: 5, propertyIRI: "http://example.org/prop" });
  });

  it("fetchAnnotations swallows 404 into empty list (pre-TS-SEMANTIC-01 channels)", async () => {
    mockList.mockRejectedValue({ response: { status: 404 } });
    const { AnnotatedChannel } = await import("~/composables/annotated");

    const out = await new AnnotatedChannel(CONTAINER, CHANNEL).fetchAnnotations();

    expect(out).toEqual([]);
  });

  it("fetchAnnotations re-throws non-404 errors", async () => {
    mockList.mockRejectedValue({ response: { status: 500 } });
    const { AnnotatedChannel } = await import("~/composables/annotated");

    await expect(
      new AnnotatedChannel(CONTAINER, CHANNEL).fetchAnnotations(),
    ).rejects.toMatchObject({ response: { status: 500 } });
  });

  it("deleteAnnotation uses cached appId from fetchAnnotations", async () => {
    mockList.mockResolvedValue([ANNO]);
    mockDelete.mockResolvedValue(undefined);
    const { AnnotatedChannel } = await import("~/composables/annotated");

    const a = new AnnotatedChannel(CONTAINER, CHANNEL);
    await a.fetchAnnotations();
    await a.deleteAnnotation(5);

    expect(mockDelete).toHaveBeenCalledWith({
      appId: CONTAINER,
      channelShepardId: CHANNEL,
      annotationAppId: ANNO_APP_ID,
    });
  });

  it("deleteAnnotation throws if appId not cached (fetchAnnotations not called)", async () => {
    const { AnnotatedChannel } = await import("~/composables/annotated");

    await expect(
      new AnnotatedChannel(CONTAINER, CHANNEL).deleteAnnotation(999),
    ).rejects.toThrow(/No appId cached/);
  });

  it("addAnnotation delegates to createChannelAnnotation and returns result", async () => {
    const created = { id: 7, appId: "new-appid", propertyIRI: "p", valueIRI: "v" };
    mockCreate.mockResolvedValue(created);
    const { AnnotatedChannel } = await import("~/composables/annotated");

    const body = { propertyIRI: "p", valueIRI: "v" };
    const out = await new AnnotatedChannel(CONTAINER, CHANNEL).addAnnotation(body);

    expect(mockCreate).toHaveBeenCalledWith({
      appId: CONTAINER,
      channelShepardId: CHANNEL,
      // compat shim fills deprecated numeric IDs until next client regen
      semanticAnnotation: { ...body, propertyRepositoryId: 0, valueRepositoryId: 0 },
    });
    expect(out).toEqual(created);
  });
});
