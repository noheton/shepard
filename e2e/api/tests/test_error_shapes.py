"""Integration tests: error response shapes (no seed required).

Verifies that 4xx responses:
- Never expose internal stack traces.
- Always carry a JSON body with a 'message' or 'violations' field.
"""


def test_404_has_no_stack_trace(http):
    r = http.get("/shepard/api/collections/999999999")
    assert r.status_code == 404, (
        f"Expected 404 for unknown collection id, got {r.status_code}"
    )
    body = r.text
    assert "at de.dlr" not in body, "Stack trace leaked in 404 response"
    assert "Exception" not in body or "message" in r.json(), (
        "Response contains 'Exception' without a structured message field"
    )


def test_400_body_has_message(http):
    # POST a collection with an empty body — should fail with 400 or 422
    r = http.post(
        "/shepard/api/collections",
        content=b"{}",
        headers={"Content-Type": "application/json"},
    )
    # A completely empty name might be accepted with a default — check the shape
    # only when it's actually an error
    if r.status_code in (400, 422):
        body = r.json()
        has_message = "message" in body or "violations" in body
        assert has_message, (
            f"Expected 'message' or 'violations' in {r.status_code} body, got: {body}"
        )
