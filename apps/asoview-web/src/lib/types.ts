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

export type OrderItemResponse = {
  id: string;
  productId: string;
  productVariantId: string;
  slotId: string;
  quantity: number;
  unitPrice: string;
};

export type OrderResponse = {
  id: string;
  userId: string;
  status: OrderStatus;
  totalAmount: string;
  currency: string;
  items: OrderItemResponse[];
  createdAt: string;
  updatedAt: string;
};

export type PaymentResponse = {
  id: string;
  orderId: string;
  status: string;
  provider: "STRIPE" | "PAYPAY" | "FAKE";
  clientSecret: string | null;
};
