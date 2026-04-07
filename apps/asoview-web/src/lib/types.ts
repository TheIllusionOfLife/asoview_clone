/**
 * Hand-written TS types mirroring the frozen REST contracts in
 * services/commerce-core. Kept hand-written (not generated) so the
 * frontend depends only on the public JSON shape, not on the Java
 * package layout.
 */

/** Spring Data `Page<T>` envelope. NB: `content: T[]`, NOT `T[]`. */
export type Page<T> = {
  content: T[];
  totalElements: number;
  number: number;
  size: number;
};

export type ProductStatus = "ACTIVE" | "DRAFT" | "ARCHIVED";
export type OrderStatus =
  | "PENDING"
  | "PAYMENT_PENDING"
  | "CONFIRMING"
  | "PAID"
  | "CANCELLED"
  | "FAILED"
  | "REFUNDED";

export type ProductVariantResponse = {
  id: string;
  name: string;
  unitPrice: string; // NUMERIC(12,2) serialised as String — never parse as Long
  currency: string;
};

export type ProductResponse = {
  id: string;
  name: string;
  description: string | null;
  status: ProductStatus;
  categoryId: string | null;
  venueId: string | null;
  variants: ProductVariantResponse[];
  averageRating?: number;
  reviewCount?: number;
};

export type AreaResponse = {
  id: string;
  name: string;
  slug: string;
};

/**
 * Single slot availability entry, as returned by
 * `GET /v1/products/{productId}/availability?from=&to=`.
 * Field names match `InventoryQueryService.AvailabilityEntry` exactly.
 */
export type AvailabilityEntry = {
  slotId: string;
  productVariantId: string;
  /** YYYY-MM-DD */
  date: string;
  /** HH:mm:ss */
  startTime: string;
  /** HH:mm:ss */
  endTime: string;
  remaining: number;
};

/** `OrderResponse.ItemResponse` from the backend. */
export type OrderItemResponse = {
  orderItemId: string;
  productVariantId: string;
  slotId: string;
  quantity: number;
  unitPrice: string;
};

/** `OrderResponse` from the backend. NB: `orderId`, not `id`. */
export type OrderResponse = {
  orderId: string;
  userId: string;
  status: OrderStatus;
  totalAmount: string;
  currency: string;
  items: OrderItemResponse[];
};

/** `CreateOrderRequest.OrderItemRequest` — note `productVariantId`. */
export type CreateOrderItemRequest = {
  productVariantId: string;
  slotId: string;
  quantity: number;
};

/** `CreateOrderRequest`. `idempotencyKey` may be omitted when sent via header. */
export type CreateOrderRequest = {
  items: CreateOrderItemRequest[];
  pointsToUse?: number;
  idempotencyKey?: string;
};

export type PaymentResponse = {
  paymentId: string;
  status: string;
  provider: string;
  providerPaymentId: string | null;
  clientSecret: string | null;
  redirectUrl: string | null;
};
