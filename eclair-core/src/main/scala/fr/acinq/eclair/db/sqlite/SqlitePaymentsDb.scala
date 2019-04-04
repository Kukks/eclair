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

package fr.acinq.eclair.db.sqlite

import java.sql.Connection
import java.util.UUID
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.eclair.db.OutgoingPayment.OutgoingPaymentStatus
import fr.acinq.eclair.db.sqlite.SqliteUtils._
import fr.acinq.eclair.db.{OutgoingPayment, PaymentsDb, ReceivedPayment}
import grizzled.slf4j.Logging
import scala.collection.immutable.Queue
import scala.compat.Platform

class SqlitePaymentsDb(sqlite: Connection) extends PaymentsDb with Logging {

  import SqliteUtils.ExtendedResultSet._

  val DB_NAME = "payments"
  val PREVIOUS_VERSION = 1
  val CURRENT_VERSION = 2

  using(sqlite.createStatement()) { statement =>
    getVersion(statement, DB_NAME, CURRENT_VERSION) match {
      case PREVIOUS_VERSION =>
        logger.warn(s"Performing db migration for paymentsDB, found version=$PREVIOUS_VERSION current=$CURRENT_VERSION")
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS payments (payment_hash BLOB NOT NULL PRIMARY KEY, amount_msat INTEGER NOT NULL, timestamp INTEGER NOT NULL)")
        statement.executeUpdate("ALTER TABLE payments RENAME TO received_payments")
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS sent_payments (id BLOB NOT NULL PRIMARY KEY, payment_hash BLOB NOT NULL, amount_msat INTEGER NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, status VARCHAR NOT NULL)")
        setVersion(statement, DB_NAME, CURRENT_VERSION)
      case CURRENT_VERSION =>
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS received_payments (payment_hash BLOB NOT NULL PRIMARY KEY, amount_msat INTEGER NOT NULL, timestamp INTEGER NOT NULL)")
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS sent_payments (id BLOB NOT NULL PRIMARY KEY, payment_hash BLOB NOT NULL, amount_msat INTEGER NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, status VARCHAR NOT NULL)")
      case unknownVersion =>
        throw new RuntimeException(s"Unknown version of paymentsDB found, version=$unknownVersion")
    }
  }

  override def addReceivedPayment(payment: ReceivedPayment): Unit = {
    using(sqlite.prepareStatement("INSERT INTO received_payments VALUES (?, ?, ?)")) { statement =>
      statement.setBytes(1, payment.paymentHash.toArray)
      statement.setLong(2, payment.amountMsat)
      statement.setLong(3, payment.timestamp)
      val res = statement.executeUpdate()
      logger.debug(s"inserted $res payment=${payment.paymentHash} into payment DB")
    }
  }

  override def updateSentStatus(id: UUID, newStatus: OutgoingPaymentStatus.Value) = {
    using(sqlite.prepareStatement(s"UPDATE sent_payments SET (status, updated_at) = (?, ?) WHERE id = ?")) { statement =>
      statement.setString(1, newStatus.toString)
      statement.setLong(2, Platform.currentTime)
      statement.setBytes(3, id.toString.getBytes)
      statement.executeUpdate()
    }
  }

  override def addSentPayment(sent: OutgoingPayment): Unit = {
    using(sqlite.prepareStatement("INSERT INTO sent_payments VALUES (?, ?, ?, ?, ?, ?)")) { statement =>
      statement.setBytes(1, sent.id.toString.getBytes)
      statement.setBytes(2, sent.paymentHash.toArray)
      statement.setLong(3, sent.amountMsat)
      statement.setLong(4, sent.createdAt)
      statement.setLong(5, sent.updatedAt)
      statement.setString(6, sent.status.toString)
      val res = statement.executeUpdate()
      logger.debug(s"inserted $res payment=${sent.paymentHash} into payment DB")
    }
  }

  override def getReceived(paymentHash: ByteVector32): Option[ReceivedPayment] = {
    using(sqlite.prepareStatement("SELECT payment_hash, amount_msat, timestamp FROM received_payments WHERE payment_hash = ?")) { statement =>
      statement.setBytes(1, paymentHash.toArray)
      val rs = statement.executeQuery()
      if (rs.next()) {
        Some(ReceivedPayment(rs.getByteVector32("payment_hash"), rs.getLong("amount_msat"), rs.getLong("timestamp")))
      } else {
        None
      }
    }
  }

  override def getSent(id: UUID): Option[OutgoingPayment] = {
    using(sqlite.prepareStatement("SELECT id, payment_hash, amount_msat, created_at, updated_at, status FROM sent_payments WHERE id = ?")) { statement =>
      statement.setBytes(1, id.toString.getBytes)
      val rs = statement.executeQuery()
      if (rs.next()) {
        Some(OutgoingPayment(
          UUID.fromString(new String(rs.getBytes("id"))),
          rs.getByteVector32("payment_hash"),
          rs.getLong("amount_msat"),
          rs.getLong("created_at"),
          rs.getLong("updated_at"),
          OutgoingPaymentStatus.withName(rs.getString("status"))))
      } else {
        None
      }
    }
  }

  override def getSent(paymentHash: ByteVector32): Option[OutgoingPayment] = {
    using(sqlite.prepareStatement("SELECT id, payment_hash, amount_msat, created_at, updated_at, status FROM sent_payments WHERE payment_hash = ?")) { statement =>
      statement.setBytes(1, paymentHash.toArray)
      val rs = statement.executeQuery()
      if (rs.next()) {
        Some(OutgoingPayment(
          UUID.fromString(new String(rs.getBytes("id"))),
          rs.getByteVector32("payment_hash"),
          rs.getLong("amount_msat"),
          rs.getLong("created_at"),
          rs.getLong("updated_at"),
          OutgoingPaymentStatus.withName(rs.getString("status"))))      } else {
        None
      }
    }
  }

  override def listReceived(): Seq[ReceivedPayment] = {
    using(sqlite.createStatement()) { statement =>
      val rs = statement.executeQuery("SELECT payment_hash, amount_msat, timestamp FROM received_payments")
      var q: Queue[ReceivedPayment] = Queue()
      while (rs.next()) {
        q = q :+ ReceivedPayment(rs.getByteVector32("payment_hash"), rs.getLong("amount_msat"), rs.getLong("timestamp"))
      }
      q
    }
  }

  override def listSent(): Seq[OutgoingPayment] = {
    using(sqlite.createStatement()) { statement =>
      val rs = statement.executeQuery("SELECT id, payment_hash, amount_msat, created_at, updated_at, status FROM sent_payments")
      var q: Queue[OutgoingPayment] = Queue()
      while (rs.next()) {
        q = q :+ OutgoingPayment(
          UUID.fromString(new String(rs.getBytes("id"))),
          rs.getByteVector32("payment_hash"),
          rs.getLong("amount_msat"),
          rs.getLong("created_at"),
          rs.getLong("updated_at"),
          OutgoingPaymentStatus.withName(rs.getString("status")))
      }
      q
    }
  }

}