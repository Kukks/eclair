/*
 * Copyright 2018 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.eclair.db

import java.util.UUID

import fr.acinq.bitcoin.ByteVector32

/**
  * Store the Lightning payments received and sent by the node. Relayed payments are not persisted.
  * <p>
  * A received payment is a [[ReceivedPayment]] object. In the local context of a LN node, it is safe to consider that
  * a payment is uniquely identified by its payment hash. As such, implementations of this database can use the payment
  * hash as a unique key and index.
  * <p>
  *
  * <p>
  * A sent payment is a [[SentPayment]] object.
  * <p>
  * Basic operations on this DB are:
  * <ul>
  * <li>insertion
  * <li>find by payment hash
  * <li>list all
  * </ul>
  */
trait PaymentsDb {

  def addReceivedPayment(payment: ReceivedPayment)

  def addSentPayments(sent: SentPayment)

  def receivedByPaymentHash(paymentHash: ByteVector32): Option[ReceivedPayment]

  def sentPaymentById(id: UUID): Option[SentPayment]

  def sentPaymentByHash(paymentHash: ByteVector32): Option[SentPayment]

  def listReceived(): Seq[ReceivedPayment]

  def listSent(): Seq[SentPayment]

}

/**
  * Received payment object stored in DB.
  *
  * @param paymentHash identifier of the payment
  * @param amount_msat amount of the payment, in milli-satoshis
  * @param timestamp   absolute time in seconds since UNIX epoch when the payment was created.
  */
case class ReceivedPayment(paymentHash: ByteVector32, amountMsat: Long, timestamp: Long)

/**
  * Sent payment object stored in DB.
  *
  * @param id           internal payment identifier
  * @param payment_hash payment_hash
  * @param amount_msat  amount of the payment, in milli-satoshis
  * @param timestamp    absolute time in seconds since UNIX epoch when the payment was created.
  */
case class SentPayment(id: UUID, paymentHash: ByteVector32, amountMsat: Long, timestamp: Long)
